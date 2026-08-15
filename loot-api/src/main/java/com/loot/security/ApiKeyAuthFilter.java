package com.loot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.ApiKey;
import com.loot.domain.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates the X-API-Key header against api_keys (stored as SHA-256 hashes,
 * never raw) for every request except webhook callbacks and /actuator/health
 * - Daraja/Flutterwave can't send an API key, and health checks shouldn't
 * need one either.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/webhooks/") || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            reject(response, "Missing X-API-Key header");
            return;
        }

        Optional<ApiKey> maybeKey = apiKeyRepository.findByKeyHash(sha256(apiKey));
        if (maybeKey.isEmpty() || !isUsable(maybeKey.get())) {
            reject(response, "Invalid or expired API key");
            return;
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "api-key:" + maybeKey.get().getId(), null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean isUsable(ApiKey key) {
        return key.isActive() && (key.getExpiresAt() == null || key.getExpiresAt().isAfter(Instant.now()));
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("errorCode", "UNAUTHORIZED", "message", message)));
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
