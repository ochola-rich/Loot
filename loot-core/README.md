# `loot-core`

The domain layer — models, value types, and the `PaymentGateway` port.
Depends on nothing else in this project. Has just enough JPA annotation
surface (`jakarta.persistence-api`, `spring-data-jpa`) for its entities to
be mapped by Hibernate once assembled into the full app by `loot-api`, but
carries no Spring Boot starter, no web layer, no persistence
implementation — this module is data shapes and contracts, not behavior
beyond what a converter or a routing decision needs.

Depended on by every other module (`loot-persistence`, `loot-gateways`,
`loot-api`); depends on none of them.

```xml
<!-- pom.xml — dependencies -->
jakarta.persistence-api
spring-data-jpa
lombok (optional)
spring-boot-starter-test (test only)
```

## `domain/model/` — entities and the one enum

| Class | Notes |
|---|---|
| `Tournament` | `id`, `name`, `entryFeeKes`, `maxEntries` (Integer), `status` (String — `OPEN`/`CLOSED`/`DISBURSED`, no enum), `createdAt`/`updatedAt`. |
| `EntryPayment` | `id`, `tournamentId`, `playerPhone` (`@Convert(PhoneNumberConverter.class)`), `amountKes`, `gateway`, `status`, `mpesaRef`. |
| `PrizeDisbursal` | `id`, `tournamentId`, `recipientPhone` (converted), `amountKes`, `gateway`, `status` (own vocabulary, not `PaymentStatus`), `gatewayRef`, `createdAt`/`updatedAt`. |
| `GatewayTransaction` | `id`, `idempotencyKey` (unique), `rawRequest`/`rawResponse` (TEXT), `gateway`, `createdAt` only — write-once, no `updatedAt`. |
| `WebhookEvent` | `id`, `gateway`, `eventType`, `requestBody`/`responseBody` (TEXT), `processedAt`, `status`, `createdAt`. |
| `ApiKey` | `id`, `keyHash` (unique, SHA-256 hex), `active`, `expiresAt` (nullable), `createdAt`. |
| `AuditEvent` | `id`, `eventType`, `details` (TEXT, JSON blob), `createdAt` only. |
| `PaymentStatus` (enum) | `INITIATED, PENDING, CONFIRMED, FAILED, REFUNDED`. |

All entities follow the same pattern: `@Getter @Setter @Entity @Table(...)`,
`@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate`/
`@LastModifiedDate` for the two write-many entities, `@CreatedDate`-only for
the two append-only ones (`GatewayTransaction`, `AuditEvent`).

> **`mpesaRef` is misleadingly named.** `EntryPayment.mpesaRef` (and the
> `PaymentRepository.findByMpesaRef` query built on it) also stores and
> looks up **Flutterwave's** `flw_ref` — `FlutterwaveWebhookController`
> calls `findByMpesaRef(data.flwRef())` directly. Deliberate, to avoid an
> unnecessary schema/naming refactor, not an oversight — but it means the
> field name doesn't imply the payment gateway; check the `gateway` column
> for that. Full schema reference: [`loot-persistence/README.md`](../loot-persistence/README.md#schema).

> **`PaymentStatus` is not the single source of truth it looks like.** It's
> used by the two gateway status mappers, but `EntryPayment.status` is
> partly set from local string constants that happen to match it, and
> `PrizeDisbursal.status`/`Tournament.status` use entirely different,
> undeclared vocabularies. Full writeup:
> [`loot-persistence/README.md`](../loot-persistence/README.md#status-vocabulary--read-this-before-adding-a-new-status-anywhere).

## `domain/money/`

- **`Money`** — a record pairing `BigDecimal amount` with a currency code,
  auto-rounding to the currency's correct decimal places (`UGX` → 0
  decimals, `KES`/`GHS`/`TZS` → 2). **Currently unused in production code**
  — every request/result record in `gateway/` (below) uses a raw
  `BigDecimal amount` + `String currency` pair instead. Not wired into
  anything yet; don't assume it's load-bearing.
- **`CurrencyGatewaySupport`** — a static map of which currencies which
  gateways support: `KES → {MPESA, FLUTTERWAVE}`, `UGX/GHS/TZS →
  {FLUTTERWAVE}` only.

## `gateway/` — the adapter port

```java
public interface PaymentGateway {
    CollectionResult initiateCollection(CollectionRequest req);
    DisbursalResult initiatePayout(DisbursalRequest req);
}

record CollectionRequest(String transactionId, String playerPhone, BigDecimal amount, String currency, String description)
record CollectionResult(boolean isSuccessful, String gatewayReference, String responseMessage)
record DisbursalRequest(String transactionId, String recipientPhone, BigDecimal amount, String currency, String description)
record DisbursalResult(boolean isSuccessful, String gatewayReference, String responseMessage)
```

This is the seam between orchestration and any specific gateway
implementation (`MpesaGateway`, `FlutterwaveGateway`) — `PaymentOrchestrator`
depends only on this interface, so adding a third gateway means writing one
new class that implements it, no changes anywhere else. Full detail on how
it's used: [`loot-gateways/README.md`](../loot-gateways/README.md).

## `crypto/`

- **`PhoneNumberConverter`** — the JPA `AttributeConverter` behind
  encrypted phone number columns (`EntryPayment.playerPhone`,
  `PrizeDisbursal.recipientPhone`), AES-256-GCM, keyed from
  `app.encryption.phone-key`. Lives here (not in `loot-persistence`)
  because the entities that `@Convert` it live here too, and `loot-core`
  already pulls in enough of Spring (`spring-context`, transitively via
  `spring-data-jpa`) for `@Component`/`@Value` constructor injection to
  work once assembled by `loot-api`. Full mechanism, key management, and
  why the equality index on these columns had to be dropped:
  [`loot-api/README.md`](../loot-api/README.md#phone-number-encryption).
