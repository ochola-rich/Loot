# `loot-persistence`

Repositories, Flyway migrations, JPA auditing config, and the financial
audit log service. Depends on `loot-core` for the entities it maps.
Depended on by `loot-gateways` (for `AuditLogService`) and `loot-api`
(for everything). This module owns "how data gets in and out of
Postgres" and nothing about HTTP or gateway integration.

```xml
<!-- pom.xml — dependencies -->
loot-core
spring-boot-starter-data-jpa
postgresql (runtime)
flyway-core
flyway-database-postgresql
spring-boot-starter-test, testcontainers (junit-jupiter, postgresql) — test only
```

## `domain/repository/`

Every repository is a plain `JpaRepository<T, Long>` — no hand-written
JPQL anywhere; every finder is a derived query method.

| Repository | Custom methods |
|---|---|
| `TournamentRepository` | `findByStatus(String)` |
| `PaymentRepository` | `findByTournamentIdAndStatus(long, String)`, `findByMpesaRef(String)` (also used for Flutterwave's `flw_ref` — see [`loot-core/README.md`](../loot-core/README.md)), `countByTournamentIdAndStatusNot(long, String)` |
| `DisbursalRepository` | `findByTournamentIdAndStatus(long, String)`, `findByGatewayRef(String)` |
| `GatewayTransactionRepository` | `findByIdempotencyKey(String)`, `findByIdempotencyKeyAndCreatedAtAfter(String, Instant)` — the latter backs the 24h idempotency window, see [`loot-api/README.md`](../loot-api/README.md#idempotency) |
| `ApiKeyRepository` | `findByKeyHash(String)` |
| `WebhookEventRepository`, `AuditEventRepository` | none — insert-only audit trails, no read-side query needs yet |

## `config/JpaAuditingConfig`

```java
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

One annotation, its own file, on purpose: `@EnableJpaAuditing` on
`LootApplication` (in `loot-api`) broke `@WebMvcTest` slice tests — they
don't load JPA infrastructure, so the auditing handler bean couldn't
resolve its dependencies. Keeping it in a dedicated persistence-module
config avoids that collision while `loot-api` still picks it up via
component scanning. Every entity is `@EntityListeners(AuditingEntityListener.class)`
with `@CreatedDate`/`@LastModifiedDate` fields populated automatically on
save/update.

`gateway_transactions` and `audit_events` are intentionally `@CreatedDate`-only
— both are meant to be immutable, append-only records; nothing ever updates
a row in either table after insert.

## `audit/AuditLogService`

The financial audit trail: three methods, `paymentInitiated`, `payoutSent`,
`gatewayFallback`, each building a small JSON blob of event-specific
fields and writing it to two places — the `audit_events` table (queryable,
permanent) and a dedicated `"AUDIT"` SLF4J logger (not the class logger,
so log shipping can filter on logger name alone):

```java
AuditEvent event = new AuditEvent();
event.setEventType(eventType);
event.setDetails(json);
repository.save(event);                     // persisted, queryable
auditLog.info("{} {}", eventType, json);    // logged, dedicated "AUDIT" logger
```

A persistence or logging failure here is caught and logged, never
propagated — by the time any of these three methods is called, the
payment/payout outcome has already been decided and persisted by the
caller, so losing an audit record shouldn't roll back or fail a payment
that already succeeded.

This is a **different trail** from `webhook_events` (raw gateway
request/response bodies, written by the gateway adapters themselves) —
`audit_events` is the small, high-level "what financial decisions did the
system make" log; `webhook_events` is the full-fidelity "what exact bytes
did we send/receive" dispute-resolution log. Don't conflate the two when
looking for something.

Callers, for reference (all outside this module):
- `PaymentController.collect` (`loot-api`) → `paymentInitiated`, called
  regardless of success/failure once the gateway outcome is known.
- `DisbursalController`'s shared `save()` helper (`loot-api`) →
  `payoutSent`, same pattern.
- `PaymentOrchestrator` (`loot-gateways`) → `gatewayFallback`, only when a
  fallback attempt actually happens — a request that succeeds on its
  primary gateway never generates this event.

This module depends on nothing that would justify AOP/retry/web concerns —
if you find yourself wanting to add an `@Retryable` or a `RestClient` call
here, it likely belongs in `loot-gateways` instead, which already owns
those dependencies for exactly this reason.

## Schema

| Table | Migration | Key columns |
|---|---|---|
| `tournaments` | V1 | `name`, `entry_fee_kes NUMERIC(12,2)`, `max_entries INT`, `status VARCHAR(20)` |
| `entry_payments` | V2, widened V8 | `tournament_id` FK, `player_phone VARCHAR(255)` (encrypted, was `VARCHAR(20)` before V8), `amount_kes`, `gateway`, `status`, `mpesa_ref VARCHAR(64)` |
| `prize_disbursals` | V3, +V6, widened V8 | `tournament_id` FK, `recipient_phone VARCHAR(255)` (encrypted), `amount_kes`, `gateway`, `status`, `gateway_ref VARCHAR(64)` (added V6) |
| `gateway_transactions` | V4 | `idempotency_key VARCHAR(64) UNIQUE`, `raw_request TEXT`, `raw_response TEXT`, `gateway`, `created_at` (no `updated_at`) |
| `webhook_events` | V5 | `gateway`, `event_type VARCHAR(50)`, `request_body TEXT`, `response_body TEXT`, `processed_at`, `status` |
| `api_keys` | V7 | `key_hash VARCHAR(64) UNIQUE`, `active BOOLEAN DEFAULT true`, `expires_at` |
| `audit_events` | V9 | `event_type VARCHAR(50)`, `details TEXT` (JSON blob), `created_at` |

All IDs are `BIGSERIAL`, all money columns are `NUMERIC(12,2)` (never
float), all timestamps are `TIMESTAMPTZ`. `spring.jpa.hibernate.ddl-auto=validate`
means Hibernate checks the entity mappings agree with what Flyway already
created, but never generates or alters schema itself — this directory is
the actual source of truth for the schema.

`db/migration/` quick reference — nine migrations, forward-only, never
edited after being applied:

| # | Adds |
|---|---|
| V1 | `tournaments` |
| V2 | `entry_payments` |
| V3 | `prize_disbursals` |
| V4 | `gateway_transactions` |
| V5 | `webhook_events` |
| V6 | `gateway_ref` column on `prize_disbursals` |
| V7 | `api_keys` |
| V8 | widens phone columns to `VARCHAR(255)` for encrypted storage, drops the now-useless `entry_payments.player_phone` equality index (`prize_disbursals.recipient_phone` never had one) |
| V9 | `audit_events` |

## Status vocabulary — read this before adding a new status anywhere

There is exactly one status enum in the codebase,
`com.loot.domain.model.PaymentStatus` (`INITIATED, PENDING, CONFIRMED,
FAILED, REFUNDED`, defined in `loot-core`), and it is **not** consistently
the source of truth for either entity's `status` column.

**`EntryPayment.status`** (plain `String` column, not the enum type) is set
two different ways depending on code path:
- `PaymentController.collect` (`loot-api`) sets it directly from local
  `String` constants (`STATUS_INITIATED = "INITIATED"`,
  `STATUS_FAILED = "FAILED"`) — these happen to match `PaymentStatus`
  values, but the controller doesn't reference the enum at all.
- The webhook controllers (`MpesaWebhookController`,
  `FlutterwaveWebhookController`, both `loot-api`) go through
  `DarajaStatusMapper`/`FlutterwaveStatusMapper` (`loot-gateways`), which
  *do* return real `PaymentStatus` enum values, stored via `.name()`.

So in practice `EntryPayment.status` values line up with `PaymentStatus`,
but by convention and duplication, not by the column being typed as the
enum or every writer referencing it.

**`PrizeDisbursal.status`** uses a **different, entirely undeclared
vocabulary**: `PROCESSING`, `CONFIRMED`, `FAILED`, `TIMEOUT` (plus
`Tournament.status`'s own `DISBURSED`, which is a tournament-level state,
not a per-disbursal one). None of these come from `PaymentStatus` — they're
local `String` constants in `DisbursalController` and raw string literals
in `MpesaWebhookController.result`/`.timeout`.
`FlutterwaveWebhookController.updateDisbursalStatus` explicitly normalizes
Flutterwave's own vocabulary (`SUCCESSFUL/FAILED/NEW/PENDING`) into this
set, with a comment acknowledging the gap directly: disbursals have
`PROCESSING`/`TIMEOUT` states a collection-only enum has no room for, and
"a second enum wasn't asked for." This is a known, self-documented
shortcut, not an accidental inconsistency — but it does mean there is no
compiler-enforced guarantee that a typo'd status string won't silently
create a new, unrecognized status value on the disbursal side. If you're
adding a new disbursal status, search for all four existing string
literals across `DisbursalController` and both webhook controllers first —
there's no single place that declares the full set today.

**`Tournament.status`** (`OPEN`, `CLOSED`, `DISBURSED`) is a third,
separate vocabulary again, also plain `String` constants, governing the
tournament lifecycle rather than any individual payment or disbursal.

## Testing

`PersistenceIntegrationTest` (`@DataJpaTest` + Testcontainers
`postgres:16-alpine`) exercises repositories against a real Postgres, not
H2 — this catches Postgres-specific behavior (e.g. the
`gateway_transactions.idempotency_key` unique constraint actually throwing
`DataIntegrityViolationException`) that an in-memory database might not
reproduce faithfully. Because `@DataJpaTest` doesn't component-scan plain
`@Component` beans by default, this test needs
`@Import(PhoneNumberConverter.class)` plus a dynamically supplied
`app.encryption.phone-key` property to persist `EntryPayment` successfully
— worth knowing if you add another `@Component`-based converter and a
`@DataJpaTest` starts failing to instantiate an entity that uses it.
