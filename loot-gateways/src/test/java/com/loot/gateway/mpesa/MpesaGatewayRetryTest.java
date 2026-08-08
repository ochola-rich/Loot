package com.loot.gateway.mpesa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.loot.domain.repository.WebhookEventRepository;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.PaymentGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MpesaGatewayTest constructs MpesaGateway directly, which never exercises
 * the Spring AOP proxy - @Retryable is inert there. This test goes through
 * a real (minimal) Spring context so the proxy is actually in play, proving
 * t34's retry-then-recover behaviour rather than just asserting on the
 * annotation being present.
 */
@SpringJUnitConfig(classes = MpesaGatewayRetryTest.RetryTestConfig.class)
class MpesaGatewayRetryTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    static {
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    // Autowired by interface, not the concrete class: @Retryable makes Spring
    // create a JDK dynamic proxy implementing PaymentGateway, not a
    // MpesaGateway subclass, so autowiring by the concrete type fails.
    @Autowired
    PaymentGateway gateway;

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(okJson("{\"access_token\":\"t\",\"expires_in\":\"3599\"}")));
    }

    @Test
    void retriesTwiceThenSucceedsOnTheThirdAttempt() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("attempt-2"));
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs("attempt-2")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("attempt-3"));
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .inScenario("retry-then-succeed")
                .whenScenarioStateIs("attempt-3")
                .willReturn(okJson("""
                        {"MerchantRequestID":"m1","CheckoutRequestID":"ws_1",
                         "ResponseCode":"0","ResponseDescription":"Success"}""")));

        CollectionResult result = gateway.initiateCollection(
                new CollectionRequest("txn-1", "254712345678", BigDecimal.valueOf(100), "KES", "Entry Fee"));

        assertThat(result.isSuccessful()).isTrue();
        wireMockServer.verify(3, postRequestedFor(urlPathEqualTo("/mpesa/stkpush/v1/processrequest")));
    }

    @Test
    void recoversToAFailedResultAfterExhaustingAllRetries() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(aResponse().withStatus(500)));

        CollectionResult result = gateway.initiateCollection(
                new CollectionRequest("txn-2", "254712345678", BigDecimal.valueOf(100), "KES", "Entry Fee"));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.responseMessage()).contains("after retries");
        wireMockServer.verify(3, postRequestedFor(urlPathEqualTo("/mpesa/stkpush/v1/processrequest")));
    }

    @Configuration
    @EnableRetry
    static class RetryTestConfig {

        @Bean
        DarajaAuthService darajaAuthService() {
            return new DarajaAuthService(baseUrl(), "key", "secret");
        }

        @Bean
        WebhookEventRepository webhookEventRepository() {
            return Mockito.mock(WebhookEventRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MpesaGateway mpesaGateway(DarajaAuthService authService, WebhookEventRepository repo, ObjectMapper mapper) {
            return new MpesaGateway(authService, baseUrl(), "174379", "passkey",
                    "https://callback.example.com", "testapi", "cred", repo, mapper);
        }

        private String baseUrl() {
            return "http://localhost:" + wireMockServer.port();
        }
    }
}
