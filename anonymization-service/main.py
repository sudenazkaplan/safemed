# anonymization service - fastapi app + rabbitmq consumer

import logging
from contextlib import asynccontextmanager
from typing import Any, Optional

from fastapi import Body, FastAPI
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel

from rabbitmq_consumer import anonymize_record, is_consumer_alive, start_consumer_thread

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("anonymization-service")

# using 27018 because local mongo already grabs 27017
MONGODB_URI = "mongodb://safemed_admin:safemed_password@localhost:27018/?authSource=admin"
MONGODB_PING_TIMEOUT_MS = 2000

# fake config store for now, will move to mongo later
privacy_config: dict[str, Any] = {
    "epsilon": 0.1,
    "masking_rules": ["name_initials", "id_partial_mask"],
    "noise_enabled": True,
}


class PrivacyConfigUpdate(BaseModel):
    # all optional so you can update just one field
    epsilon: Optional[float] = None
    masking_rules: Optional[list[str]] = None
    noise_enabled: Optional[bool] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    # start rabbitmq consumer in background
    logger.info("Starting anonymization-service ...")
    start_consumer_thread()
    yield
    # daemon thread dies with the process, nothing to clean up
    logger.info("Shutting down anonymization-service ...")


app = FastAPI(title="SafeMed Anonymization Service", lifespan=lifespan)


# mask a record directly without going through rabbitmq (handy for swagger)
@app.post("/api/v1/anonymize/process-raw")
def process_raw(record: dict[str, Any] = Body(...)):
    logger.info("Processing raw record synchronously. trackingId=%s", record.get("trackingId", "N/A"))
    return anonymize_record(record)


@app.get("/api/v1/anonymize/config")
def get_config():
    return privacy_config


@app.put("/api/v1/anonymize/config")
def update_config(update: PrivacyConfigUpdate):
    # only overwrite the fields that were actually sent
    changes = update.model_dump(exclude_none=True)
    privacy_config.update(changes)
    logger.info("Privacy configuration updated. changes=%s", changes)
    return privacy_config


# ping mongo, return UP/DOWN instead of throwing
async def _check_mongodb() -> str:
    client: Optional[AsyncIOMotorClient] = None
    try:
        client = AsyncIOMotorClient(MONGODB_URI, serverSelectionTimeoutMS=MONGODB_PING_TIMEOUT_MS)
        await client.admin.command("ping")
        return "UP"
    except Exception as exc:  # if anything blows up just mark it down
        logger.warning("MongoDB health check failed: %s", exc)
        return "DOWN"
    finally:
        if client is not None:
            client.close()


@app.get("/health")
async def health():
    rabbitmq_status = "UP" if is_consumer_alive() else "DOWN"
    mongodb_status = await _check_mongodb()

    services = {"rabbitmq": rabbitmq_status, "mongodb": mongodb_status}
    # only UP if both deps are up
    overall = "UP" if all(status == "UP" for status in services.values()) else "DEGRADED"
    return {"status": overall, "services": services}
