# rabbitmq consumer - reads the queue from the java service and masks the PII

import json
import logging
import threading
import time

import pika
from pika.exceptions import AMQPConnectionError

logger = logging.getLogger("anonymization-service.consumer")

# same creds as the java service / docker-compose
RABBITMQ_HOST = "localhost"
RABBITMQ_PORT = 5672
RABBITMQ_USERNAME = "guest"
RABBITMQ_PASSWORD = "guest"

# java declares this queue as durable too
RAW_QUEUE = "safemed.raw.queue"

RECONNECT_DELAY_SECONDS = 5


# keep first 3 and last 2, e.g. 12345678901 -> 12301
def mask_national_id(national_id: str) -> str:
    if not national_id:
        return national_id
    if len(national_id) <= 5:
        return "*" * len(national_id)
    return national_id[:3] + national_id[-2:]


# just the initials, e.g. Sudenaz Kaplan -> S K
def mask_patient_name(patient_name: str) -> str:
    if not patient_name:
        return patient_name
    initials = [part[0].upper() for part in patient_name.split() if part]
    return " ".join(initials) if initials else "*"


# only touch the two PII fields, leave the rest alone
def anonymize_record(record: dict) -> dict:
    anonymized = dict(record)
    anonymized["patientName"] = mask_patient_name(record.get("patientName", ""))
    anonymized["nationalId"] = mask_national_id(record.get("nationalId", ""))
    return anonymized


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

    channel.basic_ack(delivery_tag=method.delivery_tag)


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
