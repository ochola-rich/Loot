package com.loot.controller.webhook;

import com.loot.LootApplication;
import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.PaymentRepository;
import com.loot.domain.repository.TournamentRepository;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real Postgres (Testcontainers) and the real embedded
 * servlet stack (RANDOM_PORT), so the webhook tests exercise actual HTTP +
 * signature validation + repository persistence, not just method calls.
 *
 * The one test that hits Flutterwave's real sandbox is gated behind
 * FLW_SECRET_KEY being set - there's no way to fabricate a legitimate
 * sandbox credential, so it self-skips rather than faking a result. The
 * plan's "wait for a real webhook callback via ngrok" step isn't
 * automatable here either (needs a public URL this environment doesn't
 * have) - instead we simulate the callback Flutterwave would send and
 * verify our own handling end to end, which is what's actually testable
 * without live infrastructure.
 */
@SpringBootTest(classes = LootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FlutterwaveIntegrationTest {

    private static final String TEST_WEBHOOK_SECRET = "test-webhook-secret";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Daraja beans still need to construct even though this test is about
        // Flutterwave - dummy values, never used for a real network call here.
        registry.add("daraja.consumer-key", () -> "test");
        registry.add("daraja.consumer-secret", () -> "test");
        registry.add("daraja.passkey", () -> "test");
        registry.add("daraja.initiator-name", () -> "test");
        registry.add("daraja.security-credential", () -> "test");

        registry.add("flutterwave.webhook-secret-hash", () -> TEST_WEBHOOK_SECRET);
        registry.add("flutterwave.secret-key", () ->
                System.getenv().getOrDefault("FLW_SECRET_KEY", "FLWSECK_TEST-dummy"));

        byte[] phoneKey = new byte[32];
        new SecureRandom().nextBytes(phoneKey);
        registry.add("app.encryption.phone-key", () -> Base64.getEncoder().encodeToString(phoneKey));
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    TournamentRepository tournamentRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    @Qualifier("flutterwaveGateway")
    PaymentGateway flutterwaveGateway;

    @Test
    void webhookConfirmsAPendingPaymentOnSuccessfulCharge() {
        EntryPayment payment = seedPendingPayment("flw-ref-success-1");

        String body = """
                {"event":"charge.completed","data":{"id":123,"tx_ref":"txn-1","flw_ref":"flw-ref-success-1","status":"successful"}}""";

        ResponseEntity<Void> response = postWebhook(body, TEST_WEBHOOK_SECRET);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        EntryPayment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void webhookMarksPaymentFailedOnFailedCharge() {
        EntryPayment payment = seedPendingPayment("flw-ref-fail-1");

        String body = """
                {"event":"charge.completed","data":{"id":124,"tx_ref":"txn-2","flw_ref":"flw-ref-fail-1","status":"failed"}}""";

        postWebhook(body, TEST_WEBHOOK_SECRET);

        EntryPayment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void webhookRejectsAnInvalidVerifHash() {
        String body = """
                {"event":"charge.completed","data":{"id":125,"tx_ref":"txn-3","flw_ref":"flw-ref-3","status":"successful"}}""";

        ResponseEntity<Void> response = postWebhook(body, "wrong-secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FLW_SECRET_KEY", matches = ".+")
    void realSandboxChargeIsAcceptedByFlutterwave() {
        CollectionResult result = flutterwaveGateway.initiateCollection(
                new CollectionRequest("txn-real-1", "254708374149", new BigDecimal("10"), "KES",
                        "Integration test"));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.gatewayReference()).isNotBlank();
    }

    private EntryPayment seedPendingPayment(String flwRef) {
        Tournament tournament = new Tournament();
        tournament.setName("Integration Test Tournament");
        tournament.setEntryFeeKes(new BigDecimal("100.00"));
        tournament.setMaxEntries(10);
        tournament.setStatus("OPEN");
        tournament = tournamentRepository.save(tournament);

        EntryPayment payment = new EntryPayment();
        payment.setTournamentId(tournament.getId());
        payment.setPlayerPhone("254712345678");
        payment.setAmountKes(new BigDecimal("100.00"));
        payment.setGateway("FLUTTERWAVE");
        payment.setStatus("PENDING");
        payment.setMpesaRef(flwRef);
        return paymentRepository.save(payment);
    }

    private ResponseEntity<Void> postWebhook(String body, String verifHash) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("verif-hash", verifHash);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/webhooks/flutterwave",
                new HttpEntity<>(body, headers),
                Void.class);
    }
}
