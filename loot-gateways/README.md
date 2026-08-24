# `loot-gateways`

The M-Pesa (Daraja) and Flutterwave adapters, plus the orchestration layer
that picks between them, retries, falls back, and tracks health. This is
the most consequential business logic in the codebase — it decides where
real money goes. Depends on `loot-core` (for `PaymentGateway` and the
request/result records) and `loot-persistence` (`AuditLogService`, called
directly from `PaymentOrchestrator`'s fallback path). Depended on by
`loot-api`.

```xml
<!-- pom.xml — dependencies -->
loot-core, loot-persistence
spring-boot-starter
spring-web
jackson-databind
spring-retry
spring-boot-starter-aop          <!-- needed for @Retryable proxying -->
spring-boot-starter-test, wiremock-standalone 3.9.2 — test only
```

> `@EnableRetry` itself is declared on `loot-api`'s `LootApplication`, not
> here — this module owns the `@Retryable` annotations and the AOP
> dependency that makes proxying possible, but activation happens one
> module up. Component scanning from `com.loot` reaches down into
> `com.loot.gateway.*`, so it works today, but if this module were ever
> bootstrapped under a different application class, retry proxying would
> silently stop happening — no error, the methods would just run once.

## The `PaymentGateway` port (Adapter pattern)

`MpesaGateway` (`@Component("mpesaGateway")`) and `FlutterwaveGateway`
(`@Component("flutterwaveGateway")`) both implement `loot-core`'s
`PaymentGateway` interface (full signature in
[`loot-core/README.md`](../loot-core/README.md#gateway--the-adapter-port)).
`PaymentOrchestrator` depends only on this interface — it has no idea
Daraja or Flutterwave exist. Adding a third gateway means writing one new
class that implements `PaymentGateway` and giving it a bean name; nothing
in orchestration, routing, or the controllers changes.

## `mpesa/`

| Class | Role |
|---|---|
| `DarajaAuthService` | Fetches and caches the OAuth2 bearer token (`RestClient`, `ReentrantLock`-guarded double-check), treating it expired 60s early. |
| `StkPushRequestFactory` | Builds the STK Push body, including the `Base64(shortcode+passkey+timestamp)` password. Callback URL is built from `daraja.callback-base-url` + `/api/v1/webhooks/mpesa/confirmation`. |
| `B2CRequestFactory` | Builds the B2C payout body. `securityCredential` is taken as-is from config — **it must already be RSA-encrypted against Safaricom's public certificate before it reaches this app**; no in-app encryption of that value happens here. |
| `MpesaGateway` | Implements `PaymentGateway`. Carries `@Retryable`/`@Recover` on both methods (see below). Persists every request/response to `webhook_events` via `recordEvent`, event types `STK_PUSH_REQUEST`/`B2C_PAYOUT_REQUEST`. |
| `DarajaStatusMapper` | `resultCode == 0 → CONFIRMED`, anything else `→ FAILED`. |
| `DarajaTokenResponse`, `StkPushRequest/Response`, `B2CRequest/Response` | Jackson records for the Daraja wire format. |
| `DarajaAuthException` | Wraps token-fetch failures. |

## `flutterwave/`

| Class | Role |
|---|---|
| `FlutterwaveGateway` | Same `@Retryable`/`@Recover` shape as `MpesaGateway`. Collection: `POST /v3/charges?type=...`. Payout: `POST /v3/transfers`, hard-gated to KES only — any other currency is rejected before a network call is made. |
| `FlutterwaveChargeRequestFactory` | Maps currency → charge type: `KES→mobile_money_kenya`, `UGX→mobile_money_uganda`, `GHS→mobile_money_ghana` (`TZS` unmapped, unsupported). Synthesizes a placeholder email (`{phone}@loot.placeholder`) since the v3 charge API requires one and the domain model has no player email field. |
| `FlutterwaveTransferRequestFactory` | Hardcodes `account_bank="MPS"` — the only bank code confirmed for this integration (Kenya/M-Pesa). Deliberately doesn't use `/v3/bulk_transfers` (see Bulk payouts, below). |
| `FlutterwaveStatusMapper` | `successful→CONFIRMED`, `failed→FAILED`, `pending`/`new`/anything else/null→`PENDING`. |
| `FlutterwaveChargeRequest/Response`, `FlutterwaveTransferRequest/Response` | Jackson records (nested `Data`) for the v3 wire format. |

`GatewayHttpClients` (package root, shared by both gateways) pins each
gateway's `RestClient` to HTTP/1.1 via `JdkClientHttpRequestFactory` —
avoids RST_STREAM resets seen negotiating HTTP/2 against Jetty-backed
WireMock in tests, and kept for real traffic too.

**Currency and market constraints worth knowing:** Flutterwave payout
support is Kenya-only today (hardcoded `account_bank="MPS"`), while
collection supports Kenya/Uganda/Ghana via the charge-type map. **Uganda
and Ghana can currently collect entry fees but cannot receive prize
payouts** through this codebase as it stands.

## `orchestration/`

| Class | Role |
|---|---|
| `PaymentOrchestrator` (`@Service`) | Selects a gateway, calls it, falls back once on failure, dispatches bulk payouts across virtual threads. |
| `GatewayRoutingStrategy` (interface) | `selectGateway(CollectionRequest)`, `selectFallback(CollectionRequest, String primaryGateway)`. |
| `CountryBasedRoutingStrategy` (`@Component`) | The only implementation — routing table below. |
| `GatewayHealthRegistry` (`@Component`) | Rolling 100-outcome window per gateway, ≥80% = healthy, defaults healthy with no data. |
| `CollectionOutcome` / `DisbursalOutcome` | Records pairing a `CollectionResult`/`DisbursalResult` with the gateway name that actually produced it, since the result alone doesn't say primary-vs-fallback. |
| `PayoutExecutorConfig` (`@Configuration`) | One bean: `ExecutorService payoutExecutor()` = `Executors.newVirtualThreadPerTaskExecutor()`, `destroyMethod="close"`. |

### Routing (Strategy pattern)

`PaymentOrchestrator` is injected with a `GatewayRoutingStrategy`, not a
specific implementation — swapping routing logic (e.g. for a new market)
doesn't touch the orchestrator.

`CountryBasedRoutingStrategy`'s routing table:

| Phone prefix (after stripping `+`) | Country | Primary gateway | Fallback |
|---|---|---|---|
| `254` | Kenya | `MPESA` | `FLUTTERWAVE` |
| `256` | Uganda | `FLUTTERWAVE` | none |
| `233` | Ghana | `FLUTTERWAVE` | none |
| `255` | Tanzania | `FLUTTERWAVE` | none |
| anything else | `UNKNOWN` | `FLUTTERWAVE` | none |

Kenya is the only market with two gateways capable of handling it, so it's
the only one with a real fallback path.

`selectGateway` also does a **proactive health check**, separate from the
orchestrator's own after-the-fact fallback: if the country's preferred
gateway is currently unhealthy (via `GatewayHealthRegistry`) *and* a
fallback exists *and* that fallback is itself healthy, routing swaps to
the fallback before any network call is made at all — independent of the
per-request retry-on-failure behavior described next.

### Fallback and retry — two different mechanisms, easy to conflate

**1. Gateway fallback (`PaymentOrchestrator`)** — after a gateway call
*fails*, try the *other* gateway, once:

```java
CollectionResult result = attemptCollection(primary, req);
if (result.isSuccessful()) return new CollectionOutcome(result, primary);

String fallback = routingStrategy.selectFallback(req, primary);
if (fallback == null || fallback.equals(primary)) return new CollectionOutcome(result, primary);

auditLogService.gatewayFallback(primary, fallback, req.transactionId(), result.responseMessage());
CollectionResult fallbackResult = attemptCollection(fallback, req);
return new CollectionOutcome(fallbackResult, fallback);
```

Capped at 2 total gateway attempts. Critically, the **same** `CollectionRequest`/
`DisbursalRequest` instance is reused for the fallback call — same
`transactionId`, so if the primary gateway's failure was a false negative
(e.g. it actually went through downstream) the shared transaction ID keeps
that traceable. `GATEWAY_FALLBACK` is recorded as an audit event (see
[`loot-persistence/README.md`](../loot-persistence/README.md#auditauditlogservice))
every time this path is taken. `processPayout` follows the identical shape
for disbursals.

**2. HTTP-level retry (`@Retryable`, inside each gateway adapter)** — retries
the *same* gateway on transient HTTP failures, before orchestration ever
sees a failure:

```java
@Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 1000, multiplier = 2))
public CollectionResult initiateCollection(CollectionRequest req) { ... }

@Recover
public CollectionResult recoverCollection(HttpServerErrorException e, CollectionRequest req) { ... }
```

Both `MpesaGateway` and `FlutterwaveGateway` carry this on both
collection and payout methods: 3 attempts, 1s → 2s backoff, only on 5xx
(`HttpServerErrorException`) — never on 4xx, since a bad request won't
succeed on retry. A `@Recover` method returns a failed
`CollectionResult`/`DisbursalResult` once retries are exhausted, which is
what `PaymentOrchestrator` then sees as "this gateway failed" and reacts to
via mechanism #1 above.

**So the actual failure path for a flaky M-Pesa 503 is:** retry #1 → retry
#2 → retry #3 (each on MPESA, 1s/2s apart) → `@Recover` returns failure →
orchestrator sees MPESA failed → falls back to FLUTTERWAVE → one attempt
there. Up to 4 total gateway calls for a single collection request in the
worst case.

### Gateway health tracking

`GatewayHealthRegistry` keeps a rolling window of the last 100 outcomes per
gateway name in a synchronized `ArrayDeque`. A gateway is considered
healthy at a **≥80% success rate** over that window. A gateway with no data
yet defaults to healthy (`1.0`) — so a freshly-deployed or never-called
gateway isn't avoided just for lacking history.

`PaymentOrchestrator.attemptCollection`/`attemptPayout` call
`healthRegistry.record(gatewayName, success)` around every single gateway
call, success or failure (including when the call throws). The registry
also tracks average response time and last-checked timestamp per gateway,
exposed via `GatewayHealthController` in `loot-api`.

> **Known gap:** the only overload anything in the codebase calls,
> `record(name, success)`, passes `responseTimeMillis = -1` (untimed).
> There's a timed overload that would populate real latency data, but
> nothing calls it. In practice, `avgResponseTimeMillis` and
> `lastResponseTimeMillis` are always `-1` today, despite the field
> existing and being serialized in `GatewayHealthResponse`. Wiring up the
> timed overload in `attemptCollection`/`attemptPayout` is a small,
> contained fix if that data is ever actually needed.

### Bulk payouts — virtual threads, not a thread pool

`PayoutExecutorConfig` provides:

```java
@Bean(destroyMethod = "close")
public ExecutorService payoutExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

`PaymentOrchestrator.processBulkPayout(List<DisbursalRequest>)` submits
each payout as its own task on this executor and blocks on all the
resulting `Future`s. Each payout is a blocking HTTP call (through the same
fallback/retry machinery above) — virtual threads mean a 64-winner bulk
payout doesn't need any thread-pool sizing decision; the JVM schedules
them onto platform threads as needed and parks them cheaply while waiting
on I/O.

**Partial failure is the expected, designed-for outcome, not an edge
case.** If a `Future.get()` itself throws (task-level failure, distinct
from a gateway-level failure the task already caught and turned into a
failed `DisbursalResult`), that slot gets a synthesized failed outcome with
`gateway = null` rather than aborting the whole batch. One winner's payout
failing never affects any other winner's.

`FlutterwaveTransferRequestFactory` deliberately does **not** use
Flutterwave's own `/v3/bulk_transfers` batch endpoint — it doesn't fit the
one-request-at-a-time `PaymentGateway` contract, and the virtual-thread
dispatcher already gets the concurrency benefit a batch API would have
provided, without a second, incompatible calling convention living
alongside the single-transfer one.

## Testing

Gateway adapters are tested against WireMock (`wiremock-standalone`), not
Mockito-mocked `RestClient`s — this exercises the real HTTP client
configuration (including the HTTP/1.1 pinning above) rather than just the
request-building logic in isolation. `PaymentOrchestratorTest` mocks
`PaymentGateway` directly via Mockito, since orchestration logic (routing,
fallback, MDC population) is what's under test there, not HTTP behavior.
