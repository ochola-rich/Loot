# Loot

A tournament payments backend: collects entry fees and pays out prizes over
M-Pesa (Safaricom Daraja) and Flutterwave, with automatic gateway fallback,
health-based routing, and a virtual-thread payout dispatcher for bulk
disbursals.

Modules: `loot-core` (domain model, no framework deps beyond JPA),
`loot-persistence` (repositories, Flyway migrations), `loot-gateways`
(Daraja/Flutterwave clients, orchestration, routing), `loot-api` (REST
controllers, security, the Spring Boot entry point).

## Prerequisites

- JDK 21
- Docker (for local Postgres, and for running the full test suite -
  `PersistenceIntegrationTest` and `FlutterwaveIntegrationTest` spin up
  real Postgres via Testcontainers)

## Local setup

```bash
cp .env.example .env   # fill in Daraja/Flutterwave sandbox credentials
docker compose up --build
```

This starts Postgres, Redis (reserved for a future distributed rate
limiter - the current one is in-memory, see `RateLimitFilter`), and the
app on `:8080` under the `dev` Spring profile. Flyway runs migrations on
startup.

To run the app directly on the host instead (`./mvnw -pl loot-api spring-boot:run
-Dspring-boot.run.profiles=dev`), you'll need a local Postgres reachable at
`jdbc:postgresql://localhost:5432/lootdb` - `docker compose up postgres`
gives you just that.

### Creating an API key

Every non-webhook endpoint requires an `X-API-Key` header, checked against
a SHA-256 hash stored in `api_keys` (see `ApiKeyAuthFilter`). There's no
provisioning endpoint yet - insert one directly:

```bash
KEY="dev-local-key"
echo -n "$KEY" | sha256sum
# INSERT INTO api_keys (key_hash, active) VALUES ('<hash from above>', true);
```

## Running tests

```bash
./mvnw test
```

## Environment variables

| Variable | Required in | Notes |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | all | defaults to `loot`/`loot` in dev |
| `DATABASE_URL` | staging, prod | JDBC URL |
| `DARAJA_CONSUMER_KEY`, `DARAJA_CONSUMER_SECRET` | all | Daraja app credentials |
| `DARAJA_PASSKEY` | all | STK Push passkey |
| `DARAJA_SHORTCODE` | all | defaults to the sandbox shortcode `174379` in dev/staging |
| `DARAJA_INITIATOR_NAME`, `DARAJA_SECURITY_CREDENTIAL` | all | B2C (payout) credentials |
| `DARAJA_CALLBACK_BASE_URL` | all | public base URL Daraja calls back to |
| `DARAJA_CALLBACK_ALLOWED_IPS` | staging, prod (recommended) | comma-separated CIDRs allowed to hit `/api/v1/webhooks/mpesa/**`; left empty, callbacks are accepted from any IP with a warning logged |
| `DARAJA_BASE_URL` | optional | defaults to Safaricom's sandbox/prod URL per profile |
| `FLW_SECRET_KEY` | all | Flutterwave secret key |
| `FLW_WEBHOOK_SECRET_HASH` | all | the `verif-hash` value configured in the Flutterwave dashboard |
| `FLW_BASE_URL` | optional | defaults to `https://api.flutterwave.com` |
| `PHONE_ENCRYPTION_KEY` | staging, prod | base64-encoded 32-byte AES-256 key for phone number encryption at rest; generate with `python3 -c "import base64,os; print(base64.b64encode(os.urandom(32)).decode())"`. Dev has a built-in fallback. |

## API quick-start

```bash
API_KEY="dev-local-key"

# Create a tournament
curl -X POST localhost:8080/api/v1/tournaments \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"name":"Friday Cup","entryFeeKes":100.00,"maxEntries":64}'

# Collect an entry fee (routes to M-Pesa or Flutterwave based on phone country code)
curl -X POST localhost:8080/api/v1/payments/collect \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"tournamentId":1,"playerPhone":"+254712345678"}'

# Check payment status
curl localhost:8080/api/v1/payments/{reference}/status -H "X-API-Key: $API_KEY"

# Close the tournament, then trigger a payout
curl -X PATCH localhost:8080/api/v1/tournaments/1/close -H "X-API-Key: $API_KEY"
curl -X POST localhost:8080/api/v1/disbursals/trigger \
  -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
  -d '{"tournamentId":1,"winner":{"recipientPhone":"+254712345678","amountKes":6400.00}}'

# Gateway health snapshot
curl localhost:8080/api/v1/gateways/health -H "X-API-Key: $API_KEY"
```

Full OpenAPI docs are served at `/swagger-ui.html` while the app is
running - like everything except webhooks and `/actuator/health`, it
requires the `X-API-Key` header too, so browsing it means adding the
header via a browser extension or hitting `/v3/api-docs` with curl
instead.

## Deployment

CI (`.github/workflows/ci.yml`) runs the full test suite and pushes a
Docker image to GHCR on every push to `main`. Staging deployment
(pointing Daraja/Flutterwave sandbox webhooks at a live staging URL) is a
manual step, not yet done.
