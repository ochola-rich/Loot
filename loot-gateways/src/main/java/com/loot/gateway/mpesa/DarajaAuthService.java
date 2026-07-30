package com.loot.gateway.mpesa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fetches and caches Daraja OAuth2 bearer tokens. Tokens are valid for ~3600s;
 * we treat one as expired {@link #EXPIRY_BUFFER_SECONDS} early so a request
 * never starts mid-flight with a token that dies before the response lands.
 */
@Service
public class DarajaAuthService {

    private static final long EXPIRY_BUFFER_SECONDS = 60;

    private final RestClient restClient;
    private final String consumerKey;
    private final String consumerSecret;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public DarajaAuthService(
            @Value("${daraja.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
            @Value("${daraja.consumer-key}") String consumerKey,
            @Value("${daraja.consumer-secret}") String consumerSecret) {
        this.restClient = RestClient.create(baseUrl);
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
    }

    public String getValidToken() {
        if (isStillValid()) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (!isStillValid()) {
                refreshToken();
            }
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    private boolean isStillValid() {
        return cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS));
    }

    private void refreshToken() {
        String credentials = Base64.getEncoder()
                .encodeToString((consumerKey + ":" + consumerSecret).getBytes());

        DarajaTokenResponse response;
        try {
            response = restClient.get()
                    .uri("/oauth/v1/generate?grant_type=client_credentials")
                    .header("Authorization", "Basic " + credentials)
                    .retrieve()
                    .body(DarajaTokenResponse.class);
        } catch (Exception e) {
            throw new DarajaAuthException("Failed to obtain Daraja OAuth token", e);
        }

        if (response == null || response.accessToken() == null) {
            throw new DarajaAuthException("Daraja token response was empty");
        }

        cachedToken = response.accessToken();
        expiresAt = Instant.now().plusSeconds(Long.parseLong(response.expiresIn()));
    }
}
