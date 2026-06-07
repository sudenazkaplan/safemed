# SafeMed - Healthcare Data Ingestion & Anonymization Pipeline

SafeMed is a secure, distributed, and KVKK-compliant healthcare data management infrastructure. This repository hosts a multi-service pipeline designed to securely ingest raw clinical data, audit ingestion events, orchestrate asynchronous messaging, perform AI-driven PII redaction, and handle dual NoSQL/Relational persistence layers.

---

## Mimarinin Görsel Özeti / System Architecture

```
Clinical Client
      │  POST /api/v1/ingestion/submit
      ▼
┌─────────────────────────┐      audit      ┌──────────────┐
│  Ingestion Service       │ ──────────────▶ │  PostgreSQL  │
│  (Java / Spring Boot 4)  │                 │  audit_logs  │
└─────────────────────────┘                 └──────────────┘
      │  publish (safemed.raw.queue)
      ▼
┌─────────────────────────┐
│        RabbitMQ          │
└─────────────────────────┘
      │  consume (prefetch=1)
      ▼
┌─────────────────────────┐     persist     ┌──────────────┐
│  Anonymization Service   │ ──────────────▶ │   MongoDB    │
│  (Python / FastAPI)      │                 │ safemed_core │
└─────────────────────────┘                 └──────────────┘

   Audit queries / KVKK compliance reporting
┌─────────────────────────┐    read/write   ┌──────────────┐
│ Audit-Compliance Service │ ──────────────▶ │  PostgreSQL  │
│  (Java / Spring Boot 4)  │                 │  audit_trail │
└─────────────────────────┘                 └──────────────┘
```

---

## 🏗️ Core Ecosystem Architecture

The architecture consists of three specialized microservices communicating asynchronously via a message broker and a shared compliance database:

### 1. Ingestion Service (Java / Spring Boot 4.x)
- **Role:** Handles synchronous data entry points and enforces local data sovereignty.
- **Security & Compliance:** Intercepts incoming raw data to write immutable audit logs directly to a **PostgreSQL** database for strict compliance auditing. Only anonymized metadata (`trackingId`, `hospitalName`, `diseaseInfo`, `timestamp`) is stored — patient name and national ID are never persisted to the audit table.
- **Broker Orchestration:** Packages clinical payloads into data transfer objects (DTOs) and publishes them to RabbitMQ using a durable queue.

### 2. Redaction & Anonymization Engine (Python / FastAPI)
- **Role:** Performs decoupled data masking and formatting operations before downstream delivery.
- **AI-Driven PII Redaction:** Utilizes **Microsoft Presidio (Analyzer & Anonymizer)** with a spaCy NLP pipeline (`en_core_web_lg`) plus a custom regex recognizer to dynamically scrub patient names and localized 11-digit National IDs.
- **Differential Privacy (DP):** Implements dynamic configuration to inject metadata representing Epsilon (ε) and privacy-noise parameters.
- **Async Storage:** Persists the final sanitized JSON documents into **MongoDB** using the non-blocking asynchronous driver (**Motor**).

### 3. Audit & Compliance Logger Service (Java / Spring Boot 4.x)
- **Role:** Dedicated KVKK/GDPR audit logger that keeps an immutable audit trail of every relevant action across the pipeline.
- **Data Model:** Stores audit records in a separate **PostgreSQL** table (`audit_trail`, inside `safemed_audit_db`) holding only non-sensitive metadata: `trackingId`, `userId`, `action`, `status`, `timestamp`, and a free-text `details` field.
- **Provenance & Reporting:** Exposes a clean 5-endpoint REST API to record logs, list them globally, trace the full lifecycle of a single `trackingId`, filter anomalies, and generate a crisp compliance report (`totalOperationsLogged`, `successfulOperations`, `unauthorizedAnomalyCount`, and a calculated `complianceScore`).
- **Resilience:** Centralized error handling via `@RestControllerAdvice` returns consistent JSON error bodies with proper HTTP status codes.

---

## 🔄 End-to-End Data Pipeline Flow

1. **Ingest:** Clinical client sends a raw medical record via `POST /api/v1/ingestion/submit` to the Java Ingestion Service.
2. **Audit:** Java service logs the transaction metadata inside PostgreSQL for compliance tracking.
3. **Publish:** The raw payload is dropped into `safemed.raw.queue` hosted on RabbitMQ.
4. **Consume:** Python's background consumer thread captures the event using fair-dispatch (`prefetch_count=1`).
5. **Anonymize:** Microsoft Presidio and a custom regex recognizer parse the payload, scrubbing identifiable traits into `<REDACTED>` and `<NATIONAL_ID>`.
6. **Persist:** The anonymized object is asynchronously committed to MongoDB (`safemed_core_db.medical_records`), and only then is the broker acknowledged (`basic_ack`).

---

## 🛠️ Global Environment Topology (Docker Compose)

Core infrastructure dependencies run in containerized environments:

| Service     | Host Port | Purpose                                                        |
|-------------|-----------|----------------------------------------------------------------|
| RabbitMQ    | `5672`    | Broker communication                                           |
| RabbitMQ    | `15672`   | Management console                                             |
| PostgreSQL  | `5433`    | Shared `safemed_audit_db` — `audit_logs` + `audit_trail` (container `5432`) |
| MongoDB     | `27018`   | Anonymized storage (re-mapped to avoid a local port conflict)  |

To boot up the shared infrastructure, run from the root directory:

```bash
docker compose up -d
```

---

## 🚀 Individual Service Launch Guidelines

### Running the Ingestion Service (Java)

```bash
cd ingestion-service
./mvnw spring-boot:run
```

- Main API Port: `8080`
- Health: `http://localhost:8080/actuator/health`

### Running the Anonymization Service (Python)

```bash
cd anonymization-service
pip install -r requirements.txt
python -m spacy download en_core_web_lg
uvicorn main:app --reload --port 8081
```

- Main API Port: `8081`
- Interactive Documentation: `http://localhost:8081/docs`

### Running the Audit & Compliance Service (Java)

```bash
cd audit-compliance-service
./mvnw spring-boot:run
```

- Main API Port: `8083`
- Base Path: `http://localhost:8083/api/v1/audit`

---

## 📊 API Summary Spec

| Service | Method | Endpoint                                  | Description                                                          |
|---------|--------|-------------------------------------------|----------------------------------------------------------------------|
| Java    | POST   | `/api/v1/ingestion/submit`                | Accepts a raw clinical entry, writes an audit log, and publishes to the queue. |
| Java    | GET    | `/api/v1/ingestion/status/{trackingId}`   | Returns the processing status of a record (mock).                    |
| Java    | GET    | `/api/v1/ingestion/records`               | Lists raw record metadata (mock).                                    |
| Java    | DELETE | `/api/v1/ingestion/records/{trackingId}`  | Cancels / deletes a record from the queue.                           |
| Java    | GET    | `/actuator/health`                        | Spring Boot Actuator health (PostgreSQL + RabbitMQ).                 |
| Python  | POST   | `/api/v1/anonymize/process-raw`           | On-the-fly PII redaction utility (bypasses the queue for testing).   |
| Python  | GET    | `/api/v1/anonymize/config`                | Fetches the active runtime Differential Privacy configuration.       |
| Python  | PUT    | `/api/v1/anonymize/config`                | Updates the privacy / noise configuration at runtime.                |
| Python  | GET    | `/health`                                 | Combined health indicator for the RabbitMQ consumer and MongoDB.     |
| Audit   | POST   | `/api/v1/audit/logs`                      | Saves an incoming audit log entry.                                   |
| Audit   | GET    | `/api/v1/audit/logs`                      | Returns all audit logs globally (newest first).                      |
| Audit   | GET    | `/api/v1/audit/logs/{trackingId}`         | Returns the provenance trail for a `trackingId` (oldest first).      |
| Audit   | GET    | `/api/v1/audit/logs/filter/anomalies`     | Returns only logs where `status = 'ANOMALY'` (newest first).         |
| Audit   | GET    | `/api/v1/audit/compliance-report`         | Computes a KVKK/GDPR summary with a calculated compliance score.     |
