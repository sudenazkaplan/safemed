# SafeMed - Healthcare Data Ingestion & Anonymization Pipeline

SafeMed is a secure, distributed, and KVKK/GDPR-compliant healthcare data management infrastructure. This repository hosts a multi-service microservice ecosystem that ingests raw clinical data, audits ingestion events, orchestrates asynchronous messaging, performs AI-driven PII redaction, manages hospital schema definitions, exposes a secured researcher data-access gateway, and handles dual NoSQL/Relational persistence layers.

---

## Mimarinin Görsel Özeti / System Architecture

```
Clinical Client                                  Researcher Client
      │  POST /api/v1/ingestion/submit                 │  JWT-secured REST
      ▼                                                ▼
┌─────────────────────────┐   audit   ┌──────────────┐   ┌──────────────────────────┐
│  Ingestion Service       │ ────────▶ │  PostgreSQL  │   │ Researcher API Gateway    │
│  (Java / Spring Boot 4)  │           │  audit_logs  │   │ (Java / Spring Boot 3)    │
└─────────────────────────┘           └──────────────┘   └──────────────────────────┘
      │  publish MedicalRecordReceived                      │            │         │
      │  (safemed.exchange → safemed.raw.queue)             │ Redis      │ gRPC    │ Webhook
      ▼                                                     │ cache      │ :9090   │ (Async)
┌─────────────────────────┐                          ┌──────────┐  ┌──────────────────────────┐
│        RabbitMQ          │ ── DLQ ──▶ safemed.raw.dlq│  Redis   │  │ Audit-Compliance Service  │
└─────────────────────────┘                          └──────────┘  │ (Java / Spring Boot 4)    │
      │  consume (prefetch=1, idempotent)                           │ gRPC server + REST :8083  │
      ▼                                                             └──────────────────────────┘
┌─────────────────────────┐   persist   ┌──────────────┐                    │ persist
│  Anonymization Service   │ ──────────▶ │   MongoDB    │                    ▼
│  (Python / FastAPI)      │             │ safemed_core │           ┌──────────────┐
└─────────────────────────┘             └──────────────┘           │  PostgreSQL  │
                                                                    │  audit_trail │
┌─────────────────────────┐   CRUD      ┌──────────────┐           └──────────────┘
│  Schema Registry Service │ ──────────▶ │  PostgreSQL  │
│  (Java / Spring Boot 3)  │             │ hospital_... │
└─────────────────────────┘             └──────────────┘
```

---

## 🏗️ Core Ecosystem Architecture

The platform consists of **five application microservices** backed by **four infrastructure services** (RabbitMQ, PostgreSQL, MongoDB, Redis). Services communicate through synchronous REST, synchronous gRPC, and asynchronous AMQP (RabbitMQ) channels.

### 1. Ingestion Service (Java / Spring Boot 4.x) — port `8084`
- **Role:** Synchronous data entry point for raw clinical records; enforces local data sovereignty.
- **Compliance:** Writes immutable audit metadata (`trackingId`, `hospitalName`, `diseaseInfo`, `timestamp`) to PostgreSQL — patient name and national ID are never persisted to the audit table.
- **Event-Driven Architecture:** Publishes a past-tense **`MedicalRecordReceived`** domain event (Event-Carried State Transfer) to RabbitMQ via the `safemed.exchange` topic exchange, with the `X-Correlation-ID` and `eventId` propagated as AMQP headers.

### 2. Redaction & Anonymization Engine (Python / FastAPI) — port `8081`
- **Role:** Decoupled PII masking and persistence of sanitized records.
- **AI-Driven PII Redaction:** Uses **Microsoft Presidio (Analyzer & Anonymizer)** with a spaCy NLP pipeline (`en_core_web_lg`) plus a custom regex recognizer for localized 11-digit National IDs.
- **Differential Privacy (DP):** Injects configurable Epsilon (ε) noise metadata into numeric fields.
- **Idempotent Consumer:** Before processing, checks MongoDB for the event's `eventId`; duplicates are acknowledged and skipped to prevent at-least-once double-processing.
- **Dead Letter Queue (DLQ):** Poison messages and repeated failures are routed to `safemed.raw.dlq` via the `safemed.dlx` dead-letter exchange, emitting a **`MedicalRecordAnonymizationFailed`** event.
- **Async Storage:** Persists sanitized JSON to **MongoDB** via the non-blocking **Motor** driver; the broker is acknowledged only after a successful insert.

### 3. Schema Registry & Adapter Service (Java / Spring Boot 3.x) — port `8082`
- **Role:** Manages hospital schema definitions and mapping rules (HL7 / FHIR / JSON) through a clean 5-endpoint CRUD REST API.
- **Validation & Resilience:** DTO-based payloads validated with `@Valid`; a global `@RestControllerAdvice` returns structured JSON `404` responses via `ResourceNotFoundException`.
- **Documentation:** Springdoc OpenAPI 3 / Swagger UI exposed.

### 4. Audit & Compliance Logger Service (Java / Spring Boot 4.x) — REST `8083`, gRPC `9090`
- **Role:** Dedicated KVKK/GDPR audit logger keeping an immutable trail of pipeline actions.
- **gRPC Server:** Runs a standalone gRPC server on port `9090` (separate from the HTTP server) that receives `LogRequest` messages from the Gateway and persists them to PostgreSQL.
- **Data Model:** Stores non-sensitive metadata in the `audit_trail` table (`safemed_audit_db`): `trackingId`, `userId`, `action`, `status`, `timestamp`, `details`.
- **Provenance & Reporting:** 5-endpoint REST API to record logs, list globally, trace a `trackingId` lifecycle, filter anomalies, and generate a compliance report (`totalOperationsLogged`, `successfulOperations`, `unauthorizedAnomalyCount`, `complianceScore`).

### 5. Researcher API Gateway & Store (Java / Spring Boot 3.x) — port `8080`
- **Role:** Main public entry point for researchers; handles authentication and secured dataset access.
- **Security:** Stateless **JWT** authentication (Spring Security 6 + JJWT); passwords hashed with BCrypt.
- **Redis Caching:** Researcher dataset-request listings cached via `@Cacheable`; invalidated on lifecycle changes with `@CacheEvict`.
- **Real gRPC Integration:** `GrpcAuditClient` sends binary Protobuf audit logs over the wire to `audit-compliance-service:9090`.
- **Outbound Webhooks:** On dataset completion, fires an asynchronous (`@Async`) `DatasetReadyNotificationSent` HTTP POST (real `RestTemplate`) to the researcher's registered callback URL.
- **Documentation:** Springdoc OpenAPI 3 / Swagger UI exposed.

---

## 🔄 End-to-End Data Pipeline Flow

1. **Ingest:** Clinical client sends a raw record via `POST /api/v1/ingestion/submit`.
2. **Audit:** The Ingestion Service logs transaction metadata to PostgreSQL.
3. **Publish:** A `MedicalRecordReceived` domain event is published to `safemed.raw.queue` (with `X-Correlation-ID` header).
4. **Consume:** The Python consumer captures the event with fair-dispatch (`prefetch_count=1`).
5. **Idempotency:** The consumer checks the event's `eventId` in MongoDB and skips duplicates.
6. **Anonymize:** Presidio + custom regex scrub identifiers into `<REDACTED>` / `<NATIONAL_ID>`, and DP noise is applied.
7. **Persist:** The anonymized object is committed to MongoDB (`safemed_core_db.medical_records`); only then is `basic_ack` issued.
8. **Failure Path:** Repeated failures emit `MedicalRecordAnonymizationFailed` to the DLQ (`safemed.raw.dlq`).

Researchers, in parallel, authenticate at the Gateway, create dataset requests (audited over **gRPC** to the Audit service), and receive a `DatasetReadyNotificationSent` **webhook** when their data is ready.

---

## 🔗 Inter-Service Communication

| Flow | Style | Source → Target | Contract |
|------|-------|-----------------|----------|
| Record ingestion → anonymization | Async AMQP | Ingestion → RabbitMQ → Anonymization | `MedicalRecordReceived` (`safemed.exchange` / `safemed.routing.key` / `safemed.raw.queue`) |
| Anonymization failure handling | Async AMQP | Anonymization → DLQ | `MedicalRecordAnonymizationFailed` (`safemed.dlx` / `safemed.raw.dlq`) |
| Dataset audit logging | Sync gRPC | Gateway → Audit (`:9090`) | `AuditService.SendLog(LogRequest) → LogResponse` |
| Dataset-ready notification | Async REST (`@Async`) | Gateway → researcher callback | JSON: `status`, `downloadToken`, `message`, `correlationId` |
| Distributed tracing | Cross-cutting | All services | `X-Correlation-ID` header + SLF4J MDC |

---

## 🛠️ Global Environment Topology (Docker Compose)

| Service     | Host Port | Container Port | Purpose                                                       |
|-------------|-----------|----------------|---------------------------------------------------------------|
| RabbitMQ    | `5672`    | `5672`         | Broker communication                                          |
| RabbitMQ    | `15672`   | `15672`        | Management console                                            |
| PostgreSQL  | `5433`    | `5432`         | Shared `safemed_audit_db` (`audit_logs`, `audit_trail`, schemas, researchers) |
| MongoDB     | `27018`   | `27017`        | Anonymized storage (`safemed_core_db`)                        |
| Redis       | `6379`    | `6379`         | Gateway dataset-request cache                                 |
| Ingestion Service        | `8084` | `8084` | Raw clinical data entry                          |
| Anonymization Service    | `8081` | `8081` | PII redaction + storage                          |
| Schema Registry Service  | `8082` | `8082` | Hospital schema CRUD                             |
| Audit-Compliance Service | `8083` | `8083` | Compliance REST API                             |
| Audit-Compliance gRPC    | `9090` | `9090` | Internal gRPC audit channel                     |
| Researcher API Gateway   | `8080` | `8080` | Researcher auth + dataset access (main entry)   |

To boot the entire ecosystem from the root directory:

```bash
docker compose up --build
```

To run detached:

```bash
docker compose up -d --build
```

---

## 🚀 Individual Service Launch Guidelines

### Ingestion Service (Java)

```bash
cd ingestion-service
./mvnw spring-boot:run
```
- API Port: `8084` · Health: `http://localhost:8084/actuator/health`

### Anonymization Service (Python)

```bash
cd anonymization-service
pip install -r requirements.txt
python -m spacy download en_core_web_lg
uvicorn main:app --reload --port 8081
```
- API Port: `8081` · Docs: `http://localhost:8081/docs`

### Schema Registry Service (Java)

```bash
cd schema-registry-service
./mvnw spring-boot:run
```
- API Port: `8082` · Swagger: `http://localhost:8082/swagger-ui.html`

### Audit & Compliance Service (Java)

```bash
cd audit-compliance-service
./mvnw spring-boot:run
```
- REST Port: `8083` (base path `/api/v1/audit`) · gRPC Port: `9090`

### Researcher API Gateway (Java)

```bash
cd researcher-api-gateway
./mvnw spring-boot:run
```
- API Port: `8080` · Swagger: `http://localhost:8080/swagger-ui.html`

---

## 📊 API Summary Spec

| Service | Method | Endpoint                                  | Description                                                          |
|---------|--------|-------------------------------------------|----------------------------------------------------------------------|
| Ingestion | POST   | `/api/v1/ingestion/submit`                | Accepts a raw clinical entry, writes an audit log, and publishes `MedicalRecordReceived`. |
| Ingestion | GET    | `/api/v1/ingestion/status/{trackingId}`   | Returns the processing status of a record (mock).                    |
| Ingestion | GET    | `/api/v1/ingestion/records`               | Lists raw record metadata (mock).                                    |
| Ingestion | DELETE | `/api/v1/ingestion/records/{trackingId}`  | Cancels / deletes a record from the queue (mock).                    |
| Ingestion | GET    | `/actuator/health`                        | Spring Boot Actuator health (PostgreSQL + RabbitMQ).                 |
| Anonymization | POST | `/api/v1/anonymize/process-raw`         | On-the-fly PII redaction utility (bypasses the queue for testing).   |
| Anonymization | GET  | `/api/v1/anonymize/config`              | Fetches the active runtime Differential Privacy configuration.       |
| Anonymization | PUT  | `/api/v1/anonymize/config`              | Updates the privacy / noise configuration at runtime.                |
| Anonymization | GET  | `/health`                               | Combined health indicator for the RabbitMQ consumer and MongoDB.     |
| Schema Registry | POST   | `/api/v1/schemas`                     | Creates a hospital schema (HL7 / FHIR / JSON).                       |
| Schema Registry | GET    | `/api/v1/schemas`                     | Lists all active schemas.                                            |
| Schema Registry | GET    | `/api/v1/schemas/{id}`                | Returns a schema by id (404 JSON if missing).                        |
| Schema Registry | PUT    | `/api/v1/schemas/{id}`                | Updates a schema's mapping rules / details.                          |
| Schema Registry | DELETE | `/api/v1/schemas/{id}`                | Soft-deletes / deactivates a schema.                                 |
| Audit   | POST   | `/api/v1/audit/logs`                      | Saves an incoming audit log entry.                                   |
| Audit   | GET    | `/api/v1/audit/logs`                      | Returns all audit logs globally (newest first).                      |
| Audit   | GET    | `/api/v1/audit/logs/{trackingId}`         | Returns the provenance trail for a `trackingId` (oldest first).      |
| Audit   | GET    | `/api/v1/audit/logs/filter/anomalies`     | Returns only logs where `status = 'ANOMALY'` (newest first).         |
| Audit   | GET    | `/api/v1/audit/compliance-report`         | Computes a KVKK/GDPR summary with a calculated compliance score.     |
| Audit   | gRPC   | `AuditService/SendLog` (`:9090`)          | Receives binary audit logs from the Gateway and persists them.       |
| Gateway | POST   | `/api/v1/auth/register`                   | Registers a researcher account and returns a JWT.                    |
| Gateway | POST   | `/api/v1/auth/login`                      | Authenticates a researcher and returns a JWT.                        |
| Gateway | POST   | `/api/v1/datasets/request`                | Creates a dataset request (JWT-secured); audited over gRPC.          |
| Gateway | GET    | `/api/v1/datasets/requests/my`            | Lists the caller's dataset requests (Redis-cached).                  |
| Gateway | GET    | `/api/v1/datasets/download/{downloadToken}` | Returns anonymized records for a processed dataset.                |
| Gateway | POST   | `/api/v1/datasets/requests/{id}/complete` | Marks a request `PROCESSED` and fires the dataset-ready webhook.     |
| Gateway | POST   | `/api/v1/researchers/webhooks`            | Registers the researcher's callback URL.                            |

---

## 🧰 Technology Stack

- **Languages / Frameworks:** Java 21 (Spring Boot 3.x & 4.x, Spring MVC, Spring Security 6, Spring Data JPA, Spring AMQP, Spring Cache), Python 3.10 (FastAPI, Uvicorn)
- **Messaging:** RabbitMQ (topic exchange, durable queues, DLX/DLQ)
- **Inter-service RPC:** gRPC + Protocol Buffers (`grpc-netty-shaded`, `protobuf-maven-plugin`, `os-maven-plugin`)
- **Persistence:** PostgreSQL (relational/audit), MongoDB (anonymized documents), Redis (cache)
- **AI / Privacy:** Microsoft Presidio, spaCy `en_core_web_lg`, differential-privacy noise
- **Security:** JWT (JJWT), BCrypt
- **Documentation:** Springdoc OpenAPI 3 / Swagger UI, FastAPI OpenAPI
- **Observability:** `X-Correlation-ID` distributed tracing via SLF4J MDC
- **Containerization:** Docker (multi-stage builds), Docker Compose
