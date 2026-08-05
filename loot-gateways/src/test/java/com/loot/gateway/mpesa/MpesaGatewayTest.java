package com.loot.gateway.mpesa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.loot.domain.repository.WebhookEventRepository;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MpesaGatewayTest {

    static WireMockServer wireMockServer;

    @Mock
    WebhookEventRepository webhookEventRepository;

    MpesaGateway gateway;

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"expires_in\":\"3599\"}")));

        String baseUrl = "http://localhost:" + wireMockServer.port();
        DarajaAuthService authService = new DarajaAuthService(baseUrl, "key", "secret");

        gateway = new MpesaGateway(
                authService, baseUrl, "174379", "passkey", "https://callback.example.com",
                "testapi", "encrypted-cred", webhookEventRepository, new ObjectMapper());
    }

    @Test
    void initiateCollectionSucceedsWhenDarajaAcceptsThePush() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(okJson("""
                        {"MerchantRequestID":"m1","CheckoutRequestID":"ws_1",
                         "ResponseCode":"0","ResponseDescription":"Success. Request accepted",
                         "CustomerMessage":"Success"}""")));

        CollectionResult result = gateway.initiateCollection(
                new CollectionRequest("txn-1", "254712345678", BigDecimal.valueOf(100), "KES", "Entry Fee"));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.gatewayReference()).isEqualTo("ws_1");
        verify(webhookEventRepository).save(any());
    }

    @Test
    void initiateCollectionFailsWhenDarajaRejectsThePush() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(okJson("""
                        {"MerchantRequestID":"m1","CheckoutRequestID":"ws_1",
                         "ResponseCode":"1","ResponseDescription":"Insufficient funds"}""")));

        CollectionResult result = gateway.initiateCollection(
                new CollectionRequest("txn-1", "254712345678", BigDecimal.valueOf(100), "KES", "Entry Fee"));

        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void initiateCollectionFailsGracefullyOnHttpError() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(aResponse().withStatus(500)));

        CollectionResult result = gateway.initiateCollection(
                new CollectionRequest("txn-1", "254712345678", BigDecimal.valueOf(100), "KES", "Entry Fee"));

        assertThat(result.isSuccessful()).isFalse();
        verify(webhookEventRepository).save(any());
    }

    @Test
    void initiatePayoutSucceedsWhenDarajaAcceptsTheB2CRequest() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/b2c/v3/paymentrequest"))
                .willReturn(okJson("""
                        {"ConversationID":"conv1","OriginatorConversationID":"orig1",
                         "ResponseCode":"0","ResponseDescription":"Accept the service request successfully."}""")));

        DisbursalResult result = gateway.initiatePayout(
                new DisbursalRequest("txn-2", "254712345678", BigDecimal.valueOf(500), "KES", "Prize payout"));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.gatewayReference()).isEqualTo("conv1");
    }

    @Test
    void initiatePayoutFailsWhenDarajaRejectsTheB2CRequest() {
        wireMockServer.stubFor(post(urlPathEqualTo("/mpesa/b2c/v3/paymentrequest"))
                .willReturn(okJson("""
                        {"ConversationID":"conv1","OriginatorConversationID":"orig1",
                         "ResponseCode":"2001","ResponseDescription":"Invalid initiator credentials"}""")));

        DisbursalResult result = gateway.initiatePayout(
                new DisbursalRequest("txn-2", "254712345678", BigDecimal.valueOf(500), "KES", "Prize payout"));

        assertThat(result.isSuccessful()).isFalse();
    }
}
