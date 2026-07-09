# FinoMarket VAT Calculation Service

A high-performance, containerized Kotlin backend service built on the **Ktor** (Netty) engine to calculate VAT (Value Added Tax) for cross-border transactions.

This service utilizes a clean **Hexagonal Architecture (Ports & Adapters)** and implements key production-ready engineering patterns including:
* **Cache-Aside Pattern:** High-speed configurations/rates lookup utilizing Redis as a cache wrapper over PostgreSQL.
* **Global Interception & Context Logging:** Intercepts domain errors, carries execution context with UUIDs, and automatically finalizes database audit statuses.
* **Explicit Schema Validation:** Strong payload boundary verification before database persistence or business execution.
* **Pluggable Ingress Ports:** Defined boundaries (e.g., `TaxIngressPort`) allowing the system to easily adapt to other ingress adapters (such as Kafka/RabbitMQ) in the future without modifying core business logic.

---

## Technical Stack
* **Language:** Kotlin (1.9.22)
* **Framework:** Ktor Server (2.3.7) with Netty engine
* **Database:** PostgreSQL (15) & JetBrains Exposed ORM
* **Migrations:** Flyway
* **Cache:** Redis (7) & Jedis client
* **Build System:** Gradle Kotlin DSL
* **Orchestration:** Docker Compose

---

## Project Structure
```text
src/main/kotlin/com/example/challenge/
├── Application.kt               # Entrypoint & Ktor Plugins registration
├── config/
│   ├── DatabaseConfig.kt        # HikariCP pool, Exposed, and Flyway setup
│   └── RedisConfig.kt           # Redis JedisPool configuration
├── domain/
│   ├── model/                   # Domain entities (TaxRate, TaxRequest, AuditLog, etc.)
│   ├── exceptions/              # Domain-specific mapped exceptions
│   └── ports/
│       ├── ingress/             # Interface for incoming requests (TaxIngressPort)
│       └── egress/              # Interfaces for databases and caches (TaxRepositoryPort, CachePort)
├── adapters/
│   ├── ingress/
│   │   └── rest/                # REST Controller & payload validation logic
│   └── egress/
│       ├── db/                  # PostgreSQL repository implementation using Exposed
│       └── cache/               # Redis repository implementation using Jedis
```

---

## Local Development & Setup

### Prerequisites
* Docker & Docker Desktop

### Run the Application Stack
Start the application, PostgreSQL, and Redis containers in one command:
```bash
docker compose up --build -d
```
This builds the Ktor application inside a multi-stage Docker environment (meaning you don't need Java or Gradle installed on your host OS) and runs all dependencies.

### Verification Endpoints

#### 1. Service Health Check
```bash
curl http://localhost:8080/health
```
*Response:*
```json
{
  "status": "UP",
  "message": "Ktor tax service is running"
}
```

#### 2. Calculate VAT (Success / Cache-Aside Miss & Hit)
```bash
curl -X POST http://localhost:8080/tax/calculate \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.0, "countryCode": "DE", "category": "ELECTRONICS"}'
```
* **First request (Cache Miss):** Hits the database, populates Redis, inserts `SUCCESS` audit log, and returns calculation.
* **Subsequent requests (Cache Hit):** Reads the rate instantly from Redis, bypassing the database.

#### 3. Payload Validation / Exception Handling
```bash
curl -X POST http://localhost:8080/tax/calculate \
  -H "Content-Type: application/json" \
  -d '{"amount": -50.0, "countryCode": "DE", "category": "ELECTRONICS"}'
```
*Returns custom error mapped through Ktor StatusPages, and finalizes audit log status to `FAILED` in the database:*
```json
{
  "success": false,
  "errorCode": "INVALID_PAYLOAD",
  "message": "Amount must be greater than zero"
}
```

---

## Production Deployment (Railway)

This repository is pre-configured to build and run on **Railway**. 

1. Create a new project in Railway.
2. Connect your GitHub repository.
3. Add a **PostgreSQL** database and a **Redis** service.
4. Bind the following environment variables to the web service:
   * `SPRING_DATASOURCE_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   * `SPRING_DATASOURCE_USERNAME` = `${{Postgres.PGUSER}}`
   * `SPRING_DATASOURCE_PASSWORD` = `${{Postgres.PGPASSWORD}}`
   * `REDIS_HOST` = `${{Redis.REDISHOST}}`
   * `REDIS_PORT` = `${{Redis.REDISPORT}}`
