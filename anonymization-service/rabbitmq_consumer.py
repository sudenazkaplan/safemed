# rabbitmq consumer - reads the queue from the java service, runs presidio, stores to mongo

import asyncio
import json
import logging
import math
import random
import threading
import time

import pika
from pika.exceptions import AMQPConnectionError
from motor.motor_asyncio import AsyncIOMotorClient
from presidio_analyzer import AnalyzerEngine, Pattern, PatternRecognizer
from presidio_anonymizer import AnonymizerEngine
from presidio_anonymizer.entities import OperatorConfig

logger = logging.getLogger("anonymization-service.consumer")

# same creds as the java service / docker-compose
RABBITMQ_HOST = "localhost"
RABBITMQ_PORT = 5672
RABBITMQ_USERNAME = "guest"
RABBITMQ_PASSWORD = "guest"

# java declares this queue as durable too
RAW_QUEUE = "safemed.raw.queue"

RECONNECT_DELAY_SECONDS = 5

# mongo target (27018 because local mongo grabs 27017)
MONGODB_URI = "mongodb://safemed_admin:safemed_password@localhost:27018/?authSource=admin"
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


def _on_message(channel, method, properties, body) -> None:
    try:
        original = json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        logger.error("Failed to parse message payload, discarding. error=%s", exc)
        # drop bad messages so they don't loop forever
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        return

    anonymized = anonymize_record(original)

    tracking_id = original.get("trackingId", "N/A")
    logger.info("Message received. trackingId=%s", tracking_id)
    logger.info("Original   : %s", original)
    logger.info("Anonymized : %s", anonymized)

    # only ack once it's safely stored in mongo
    try:
        asyncio.run(_store_record(dict(anonymized)))
    except Exception as exc:
        logger.error("Mongo insert failed, message not acked. error=%s", exc)
        # leave it on the queue to retry later
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        return

    channel.basic_ack(delivery_tag=method.delivery_tag)
    logger.info("Stored in mongo and acked. trackingId=%s", tracking_id)


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

            # must be durable or rabbitmq throws PRECONDITION_FAILED
            channel.queue_declare(queue=RAW_QUEUE, durable=True)

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
