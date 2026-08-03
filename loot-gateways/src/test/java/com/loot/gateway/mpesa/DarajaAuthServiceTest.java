package com.loot.gateway.mpesa;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DarajaAuthServiceTest {

    static WireMockServer wireMockServer;

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
    void resetServer() {
        wireMockServer.resetAll();
    }

    private DarajaAuthService authService() {
        return new DarajaAuthService("http://localhost:" + wireMockServer.port(), "key", "secret");
    }

    @Test
    void cachesTokenAcrossMultipleCallsWithinItsLifetime() {
        wireMockServer.stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(okJson("{\"access_token\":\"abc123\",\"expires_in\":\"3599\"}")));

        DarajaAuthService authService = authService();

        assertThat(authService.getValidToken()).isEqualTo("abc123");
        assertThat(authService.getValidToken()).isEqualTo("abc123");
        assertThat(authService.getValidToken()).isEqualTo("abc123");

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/oauth/v1/generate")));
    }

    @Test
    void sendsBasicAuthHeaderBuiltFromConsumerKeyAndSecret() {
        wireMockServer.stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(okJson("{\"access_token\":\"abc123\",\"expires_in\":\"3599\"}")));

        authService().getValidToken();

        String expected = "Basic " + java.util.Base64.getEncoder().encodeToString("key:secret".getBytes());
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/oauth/v1/generate"))
                .withHeader("Authorization", equalTo(expected)));
    }

    @Test
    void throwsWhenDarajaReturnsAnErrorResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse().withStatus(500)));

        DarajaAuthService authService = authService();

        assertThrows(DarajaAuthException.class, authService::getValidToken);
    }
}
