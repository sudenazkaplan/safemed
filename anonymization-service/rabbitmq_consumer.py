# rabbitmq consumer - reads the queue from the java service, runs presidio, stores to mongo

import asyncio
import json
import logging
import math
import os
import random
import threading
import time
import uuid
from datetime import datetime, timezone

import pika
from pika.exceptions import AMQPConnectionError
from motor.motor_asyncio import AsyncIOMotorClient
from presidio_analyzer import AnalyzerEngine, Pattern, PatternRecognizer
from presidio_anonymizer import AnonymizerEngine
from presidio_anonymizer.entities import OperatorConfig

logger = logging.getLogger("anonymization-service.consumer")

# same creds as the java service / docker-compose
# host comes from env so it works both locally and inside docker
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = 5672
RABBITMQ_USERNAME = "guest"
RABBITMQ_PASSWORD = "guest"

# java declares this queue as durable too
RAW_QUEUE = "safemed.raw.queue"

# dead letter side, must match the java RabbitMQConfig
DLX_EXCHANGE = "safemed.dlx"
DLQ = "safemed.raw.dlq"
DLQ_ROUTING_KEY = "safemed.raw.dlq"

# trace header set by the java producer
CORRELATION_HEADER = "X-Correlation-ID"

RECONNECT_DELAY_SECONDS = 5

# mongo target, overridden by env inside docker
MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
MONGO_DB = "safemed_core_db"
MONGO_COLLECTION = "medical_records"

# masking + differential privacy config, shared with the api in main.py
privacy_config = {
    "epsilon": 0.1,
    "masking_rules": ["name_initials", "id_partial_mask"],
    "noise_enabled": True,
}

# presidio engines are heavy, build them once on first use
_analyzer = None
_anonymizer = None


def _get_engines():
    global _analyzer, _anonymizer
    if _analyzer is None:
        _analyzer = AnalyzerEngine()
        # 11-digit national id isn't a default presidio entity, add our own
        national_id = Pattern(name="national_id_11", regex=r"\b\d{11}\b", score=0.9)
        _analyzer.registry.add_recognizer(
            PatternRecognizer(supported_entity="NATIONAL_ID", patterns=[national_id])
        )
        _anonymizer = AnonymizerEngine()
    return _analyzer, _anonymizer


# run a string through presidio and return the masked version
def _scrub(text):
    if not text:
        return text
    analyzer, anonymizer = _get_engines()
    results = analyzer.analyze(
        text=text,
        language="en",
        entities=["PERSON", "NATIONAL_ID", "PHONE_NUMBER", "EMAIL_ADDRESS"],
    )
    if not results:
        return text
    operators = {
        "NATIONAL_ID": OperatorConfig("replace", {"new_value": "<NATIONAL_ID>"}),
        "DEFAULT": OperatorConfig("replace", {"new_value": "<REDACTED>"}),
    }
    return anonymizer.anonymize(text=text, analyzer_results=results, operators=operators).text


# laplace sample for the epsilon noise
def _laplace(scale):
    u = random.random() - 0.5
    return -scale * math.copysign(1, u) * math.log(1 - 2 * abs(u))


# add epsilon noise to numeric metadata, plus a marker so the DP step is visible
def _apply_dp_noise(record):
    eps = privacy_config.get("epsilon") or 0.1
    out = dict(record)
    if isinstance(out.get("age"), (int, float)):
        out["age"] = round(out["age"] + _laplace(1.0 / eps))
    out["dpNoise"] = {"enabled": True, "epsilon": eps}
    return out


# scrub PII with presidio, then optional DP noise
def anonymize_record(record: dict) -> dict:
    clean = dict(record)
    if "patientName" in clean:
        clean["patientName"] = _scrub(str(clean.get("patientName") or ""))
    if "nationalId" in clean:
        clean["nationalId"] = _scrub(str(clean.get("nationalId") or ""))
    if privacy_config.get("noise_enabled"):
        clean = _apply_dp_noise(clean)
    return clean


# motor is async, insert the clean doc into safemed_core_db.medical_records
async def _store_record(doc: dict) -> None:
    client = AsyncIOMotorClient(MONGODB_URI, serverSelectionTimeoutMS=3000)
    try:
        await client[MONGO_DB][MONGO_COLLECTION].insert_one(doc)
    finally:
        client.close()


# idempotent consumer: have we already stored this eventId?
async def _already_processed(event_id: str) -> bool:
    client = AsyncIOMotorClient(MONGODB_URI, serverSelectionTimeoutMS=3000)
    try:
        existing = await client[MONGO_DB][MONGO_COLLECTION].find_one({"eventId": event_id})
        return existing is not None
    finally:
        client.close()


# build a MedicalRecordAnonymizationFailed event and push it to the DLQ
def _publish_failure_event(channel, event: dict, correlation_id: str, reason: str) -> None:
    failure = {
        "eventId": str(uuid.uuid4()),
        "eventType": "MedicalRecordAnonymizationFailed",
        "correlationId": correlation_id,
        "trackingId": event.get("trackingId", "N/A"),
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "reason": reason,
        # carry the original state along so a DLQ consumer doesn't need a lookup
        "patientName": event.get("patientName"),
        "nationalId": event.get("nationalId"),
        "diseaseInfo": event.get("diseaseInfo"),
        "hospitalName": event.get("hospitalName"),
    }
    channel.basic_publish(
        exchange=DLX_EXCHANGE,
        routing_key=DLQ_ROUTING_KEY,
        body=json.dumps(failure).encode("utf-8"),
        properties=pika.BasicProperties(
            headers={CORRELATION_HEADER: correlation_id},
            delivery_mode=2,  # persistent
        ),
    )


# pull a header off the amqp properties, default to N/A
def _header(properties, key) -> str:
    if properties and properties.headers:
        return properties.headers.get(key, "N/A")
    return "N/A"


def _on_message(channel, method, properties, body) -> None:
    correlation_id = _header(properties, CORRELATION_HEADER)

    try:
        event = json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        logger.error("Failed to parse payload, dead-lettering. error=%s [X-Correlation-ID: %s]", exc, correlation_id)
        # poison message, send it straight to the DLQ via the queue's DLX
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        return

    event_id = event.get("eventId")
    event_type = event.get("eventType", "Unknown")
    tracking_id = event.get("trackingId", "N/A")
    logger.info(
        "Event received. type=%s eventId=%s trackingId=%s [X-Correlation-ID: %s]",
        event_type, event_id, tracking_id, correlation_id,
    )

    # idempotent consumer: skip if this eventId is already stored (at-least-once safety)
    try:
        if event_id and asyncio.run(_already_processed(event_id)):
            logger.info("Duplicate event, already processed. Skipping. eventId=%s [X-Correlation-ID: %s]",
                        event_id, correlation_id)
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return
    except Exception as exc:
        logger.error("Idempotency check failed, requeueing. error=%s [X-Correlation-ID: %s]", exc, correlation_id)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        return

    anonymized = anonymize_record(event)
    logger.info("Original   : %s", event)
    logger.info("Anonymized : %s", anonymized)

    # only ack once it's safely stored in mongo
    try:
        asyncio.run(_store_record(dict(anonymized)))
    except Exception as exc:
        if not method.redelivered:
            # first failure, give it one retry on the main queue
            logger.warning("Mongo insert failed, requeueing once. error=%s [X-Correlation-ID: %s]", exc, correlation_id)
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        else:
            # failed repeatedly -> emit a failure event into the DLQ and let go of the original
            logger.error("Mongo insert failed again, routing MedicalRecordAnonymizationFailed to DLQ. "
                         "error=%s [X-Correlation-ID: %s]", exc, correlation_id)
            _publish_failure_event(channel, event, correlation_id, f"Mongo insert failed: {exc}")
            channel.basic_ack(delivery_tag=method.delivery_tag)
        return

    channel.basic_ack(delivery_tag=method.delivery_tag)
    logger.info("Stored in mongo and acked. eventId=%s trackingId=%s [X-Correlation-ID: %s]",
                event_id, tracking_id, correlation_id)


def _consume_loop() -> None:
    credentials = pika.PlainCredentials(RABBITMQ_USERNAME, RABBITMQ_PASSWORD)
    parameters = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=RABBITMQ_PORT,
        credentials=credentials,
        heartbeat=60,
        blocked_connection_timeout=30,
    )

    while True:
        try:
            logger.info("Connecting to RabbitMQ at %s:%s ...", RABBITMQ_HOST, RABBITMQ_PORT)
            connection = pika.BlockingConnection(parameters)
            channel = connection.channel()

            # declare the dead letter topology first (idempotent, matches the java side)
            channel.exchange_declare(exchange=DLX_EXCHANGE, exchange_type="direct", durable=True)
            channel.queue_declare(queue=DLQ, durable=True)
            channel.queue_bind(queue=DLQ, exchange=DLX_EXCHANGE, routing_key=DLQ_ROUTING_KEY)

            # main queue must carry the same dlx args or rabbitmq throws PRECONDITION_FAILED
            channel.queue_declare(
                queue=RAW_QUEUE,
                durable=True,
                arguments={
                    "x-dead-letter-exchange": DLX_EXCHANGE,
                    "x-dead-letter-routing-key": DLQ_ROUTING_KEY,
                },
            )

            # one message at a time
            channel.basic_qos(prefetch_count=1)
            channel.basic_consume(queue=RAW_QUEUE, on_message_callback=_on_message)

            logger.info("Listening on queue '%s'. Waiting for messages ...", RAW_QUEUE)
            channel.start_consuming()
        except AMQPConnectionError as exc:
            logger.warning(
                "RabbitMQ connection failed (%s). Retrying in %ss ...",
                exc,
                RECONNECT_DELAY_SECONDS,
            )
            time.sleep(RECONNECT_DELAY_SECONDS)
        except Exception:  # don't let the thread die, just retry
            logger.exception("Unexpected error in consumer loop. Retrying in %ss ...", RECONNECT_DELAY_SECONDS)
            time.sleep(RECONNECT_DELAY_SECONDS)


_consumer_thread: "threading.Thread | None" = None


def start_consumer_thread() -> threading.Thread:
    global _consumer_thread
    _consumer_thread = threading.Thread(target=_consume_loop, name="rabbitmq-consumer", daemon=True)
    _consumer_thread.start()
    logger.info("RabbitMQ consumer thread started.")
    return _consumer_thread


# used by the /health endpoint
def is_consumer_alive() -> bool:
    return _consumer_thread is not None and _consumer_thread.is_alive()
