# FinoMarket Cross-Border VAT Calculation Service

A containerized Kotlin/Ktor backend service that calculates VAT/IVA for cross-border e-commerce transactions between LATAM (Mexico, Brazil, Colombia, Chile) and Europe (UK, Germany, Spain, France), and audits historical transactions for tax compliance gaps.

Built with a clean **Hexagonal Architecture (Ports & Adapters)**:
* **Pure rule engine** (`VatRuleEngine`): stateless, fully unit-testable cross-border jurisdiction logic.
* **Cache-Aside pattern:** rate lookups served from Redis with PostgreSQL as the source of truth (Flyway-seeded).
* **Full audit trail:** every API call's raw payload is persisted with a request UUID and finalized to `SUCCESS`/`FAILED`.
* **Global error interception:** domain errors are mapped to structured JSON errors via Ktor StatusPages.

---

## Technical Stack
* **Language:** Kotlin 1.9 · **Framework:** Ktor 2.3 (Netty)
* **Database:** PostgreSQL 15 + JetBrains Exposed + Flyway migrations
* **Cache:** Redis 7 (Jedis) — the service degrades gracefully if Redis is down
* **Build/Run:** Gradle Kotlin DSL, Docker Compose (no local JDK needed)

## How to Run

```bash
docker compose up --build -d
```

Builds the app in a multi-stage Docker image and starts app + PostgreSQL + Redis. The service listens on **http://localhost:8080**. Verify with:

```bash
curl http://localhost:8080/health
```

Run unit tests (rule-engine matrix, money rounding, batch classification):

```bash
docker run --rm -v "$PWD:/home/gradle/src" -w /home/gradle/src gradle:8.5.0-jdk17 gradle test --no-daemon
```

---

## API Documentation

### 1. `POST /tax/calculate` — Core Requirement 1

Calculates the correct VAT for a single transaction at checkout time.

**Request fields**

| Field | Required | Values |
|---|---|---|
| `sellerCountry` / `buyerCountry` | yes | `MX`, `BR`, `CO`, `CL`, `UK`, `DE`, `ES`, `FR` (codes or full names, case-insensitive) |
| `productType` | yes | `PHYSICAL`, `DIGITAL`, `SERVICES` |
| `transactionType` | yes | `B2C`, `B2B` |
| `netAmount` | yes | pre-tax price, > 0 |
| `currency` | yes | `USD`, `EUR`, `GBP`, `MXN`, `BRL`, `COP`, `CLP` |
| `productCategory` | no | `STANDARD` (default), `BOOKS`, `FOOD` — Stretch Goal 1 |
| `reportingCurrency` | no | any supported currency — Stretch Goal 2 |

**Example — Mexican seller, German buyer, digital B2C (destination-based digital rule):**

```bash
curl -X POST http://localhost:8080/tax/calculate \
  -H "Content-Type: application/json" \
  -d '{"sellerCountry":"MX","buyerCountry":"DE","productType":"DIGITAL","transactionType":"B2C","netAmount":100.0,"currency":"USD"}'
```

```json
{
  "sellerCountry": "MX",
  "buyerCountry": "DE",
  "productType": "DIGITAL",
  "transactionType": "B2C",
  "productCategory": "STANDARD",
  "netAmount": 100.0,
  "currency": "USD",
  "taxRate": 19.0,
  "taxAmount": 19.0,
  "totalAmount": 119.0,
  "taxJurisdiction": "DE",
  "reasoning": "Germany VAT applies: B2C digital goods sold to an EU buyer are always taxed at the buyer's country rate (destination-based taxation), even for a non-EU seller."
}
```

**Example — reduced category + reporting currency (both stretch goals):**

```bash
curl -X POST http://localhost:8080/tax/calculate \
  -H "Content-Type: application/json" \
  -d '{"sellerCountry":"FR","buyerCountry":"DE","productType":"PHYSICAL","transactionType":"B2C","netAmount":100.0,"currency":"EUR","productCategory":"BOOKS","reportingCurrency":"USD"}'
```

Returns German reduced book rate (7%) plus a `reporting` block with amounts converted to USD and the exchange rate used.

Zero-rated cases (exports, reverse charge, import-VAT-on-buyer) return `"taxJurisdiction": "NONE"` with a reasoning line naming the rule.

### 2. `POST /tax/validate-batch` — Core Requirement 2

Audits historical transactions: recalculates what the tax should have been and classifies each row.

```bash
curl -X POST http://localhost:8080/tax/validate-batch \
  -H "Content-Type: application/json" \
  -d @test-data/historical-transactions.json
```

Each transaction carries the same fields as `/tax/calculate` plus `id` and `chargedTaxAmount`. Response:

```json
{
  "summary": {
    "totalTransactions": 20,
    "correctCount": 10,
    "overchargedCount": 5,
    "underchargedCount": 5,
    "invalidCount": 0,
    "netDiscrepancyByCurrency": { "USD": 64.0, "EUR": 57.1, "GBP": 8.0, "CLP": 171.0, "BRL": -10.0 },
    "absoluteDiscrepancyByCurrency": { "USD": 64.0, "EUR": 156.9, "GBP": 32.0, "CLP": 171.0, "BRL": 10.0 }
  },
  "results": [
    {
      "id": "h-011",
      "status": "OVERCHARGED",
      "currency": "USD",
      "expectedTaxRate": 0.0,
      "expectedTaxAmount": 0.0,
      "chargedTaxAmount": 64.0,
      "discrepancy": 64.0,
      "taxJurisdiction": "NONE",
      "reasoning": "Physical goods exported from Mexico to Spain: 0% — the buyer pays import VAT separately at customs."
    }
  ]
}
```

* `status`: `CORRECT` (within ±0.01 rounding tolerance) | `OVERCHARGED` | `UNDERCHARGED` | `INVALID` (unparseable row — reported, never fails the batch).
* `discrepancy` = charged − expected (positive = merchant overcharged the buyer).
* `netDiscrepancyByCurrency` is the net compliance exposure; `absoluteDiscrepancyByCurrency` is total error magnitude.

### 3. `GET /health`

Liveness check.

---

## Tax Rules Implemented

| # | Scenario | Result |
|---|---|---|
| 1 | Same country (LATAM or EU) | Seller country's standard/category rate |
| 2 | LATAM → LATAM (different) | 0% — export, buyer handles import taxes |
| 3 | LATAM → EU, digital B2C | **Buyer's country rate** (destination-based digital rule) |
| 4 | LATAM → EU, digital B2B | 0% — reverse charge |
| 5 | LATAM → EU, physical | 0% — buyer pays import VAT at customs |
| 6 | LATAM → EU, services | 0% — export |
| 7 | EU → EU, B2C (different) | Buyer's country rate (destination-based) |
| 8 | EU → EU, B2B (different) | 0% — reverse charge |
| 9 | EU → LATAM | 0% — export |

**Rates** (seeded via Flyway `V2` migration): MX 16 · BR 17 · CO 19 · CL 19 · UK 20 · DE 19 · ES 21 · FR 20.
**Reduced (Stretch 1):** BOOKS — UK 0, DE 7, ES 4, FR 5.5 · FOOD — UK 0, DE 7, ES 10, FR 5.5.

## Test Data

* [test-data/calculation-scenarios.json](test-data/calculation-scenarios.json) — 52 scenarios covering every rule branch, all product/transaction types, all 7 currencies, reduced categories, and reporting-currency conversion. Each includes the expected outcome.
* [test-data/historical-transactions.json](test-data/historical-transactions.json) — 20 historical transactions (10 correct, 5 overcharged, 5 undercharged) in 6 currencies; POST it directly to `/tax/validate-batch`. The expected summary is embedded in the file under `_expectedSummary`.
* Run all 52 scenarios against a live instance:
  ```powershell
  powershell -File scripts\run-scenarios.ps1
  ```
* **Postman:** import [postman/FinoMarket-VAT.postman_collection.json](postman/FinoMarket-VAT.postman_collection.json) — ready-made requests for every endpoint, including the full 20-transaction audit batch. Base URL is a collection variable (`baseUrl`, default `http://localhost:8080`).

## Assumptions & Simplifications

* **UK is treated as part of the "EU" region**, per the challenge spec (post-Brexit rules are more nuanced).
* Seller is assumed VAT-registered in every relevant jurisdiction; **distance-selling / small-seller thresholds (€10,000/yr) are ignored**.
* Domestic B2B sales are charged VAT normally (no domestic reverse charge).
* Brazil uses a simplified 17% national average (real ICMS/PIS/COFINS varies by state).
* LATAM countries have no reduced BOOKS/FOOD rates in this prototype — they fall back to the standard rate.
* FX rates are hardcoded snapshots; COP/CLP amounts are still rounded to 2 decimals for consistency.
* Tax is rounded per transaction to 2 decimals, HALF_UP, using `BigDecimal` (no floating-point drift).

## What I'd Improve With More Time

* **Authentication & authorization**: API keys/OAuth per merchant with role-based permissions, so multiple merchants (like FinoMarket) can use the service in isolation.
* **Better data governance**: rate versioning with effective dates (`valid_from`/`valid_to`) so historical audits use the rate in force at transaction time, plus retention policies for audit logs.
* **Stronger schema validations** at the API boundary (max amounts, currency-jurisdiction consistency checks, request size limits).
* **Notifications**: alert merchants (email/webhook) when a batch audit finds compliance discrepancies above a threshold.
* **VAT number validation** (VIES lookup) before applying B2B reverse charge.
* Real FX provider with rates locked at transaction time; currency-aware decimal places (0 for CLP/COP).
* Distance-selling threshold tracking per seller/country (the €10,000 rule) and OSS/IOSS registration modeling.
* Idempotency keys on `/tax/calculate`, OpenAPI spec, and integration tests with Testcontainers.
* Pagination/streaming for very large audit batches.

---

## Production Deployment (Railway)

1. Create a new Railway project and connect this repository.
2. Add **PostgreSQL** and **Redis** services.
3. Bind these variables to the web service:
   * `SPRING_DATASOURCE_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   * `SPRING_DATASOURCE_USERNAME` = `${{Postgres.PGUSER}}`
   * `SPRING_DATASOURCE_PASSWORD` = `${{Postgres.PGPASSWORD}}`
   * `REDIS_HOST` = `${{Redis.REDISHOST}}`
   * `REDIS_PORT` = `${{Redis.REDISPORT}}`
