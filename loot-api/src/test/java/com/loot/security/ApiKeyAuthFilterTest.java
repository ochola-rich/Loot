package com.loot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.ApiKey;
import com.loot.domain.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    private static final String RAW_KEY = "loot_test_key_123";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(apiKeyRepository, new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRequestWithNoApiKeyHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void rejectsUnknownApiKey() throws Exception {
        when(apiKeyRepository.findByKeyHash(sha256(RAW_KEY))).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        request.addHeader("X-API-Key", RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void rejectsInactiveApiKey() throws Exception {
        ApiKey key = validKey();
        key.setActive(false);
        when(apiKeyRepository.findByKeyHash(sha256(RAW_KEY))).thenReturn(Optional.of(key));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        request.addHeader("X-API-Key", RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredApiKey() throws Exception {
        ApiKey key = validKey();
        key.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(apiKeyRepository.findByKeyHash(sha256(RAW_KEY))).thenReturn(Optional.of(key));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        request.addHeader("X-API-Key", RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsActiveUnexpiredKeyAndSetsAuthentication() throws Exception {
        ApiKey key = validKey();
        when(apiKeyRepository.findByKeyHash(sha256(RAW_KEY))).thenReturn(Optional.of(key));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        request.addHeader("X-API-Key", RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void skipsWebhookAndHealthPathsWithoutRequiringAKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhooks/mpesa/confirmation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private ApiKey validKey() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setKeyHash(sha256(RAW_KEY));
        key.setActive(true);
        key.setCreatedAt(Instant.now());
        return key;
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
