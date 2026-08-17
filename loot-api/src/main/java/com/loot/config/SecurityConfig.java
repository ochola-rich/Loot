package com.loot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.repository.ApiKeyRepository;
import com.loot.security.ApiKeyAuthFilter;
import com.loot.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Webhook endpoints and /actuator/health are reachable without auth
 * (Daraja/Flutterwave can't authenticate as us); everything else requires a
 * valid X-API-Key, checked by ApiKeyAuthFilter before Spring Security's own
 * UsernamePasswordAuthenticationFilter runs. This is a stateless REST API so
 * CSRF protection (meant for browser session/cookie auth) doesn't apply.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter, RateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/webhooks/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, ApiKeyAuthFilter.class);
        return http.build();
    }

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyRepository apiKeyRepository, ObjectMapper objectMapper) {
        return new ApiKeyAuthFilter(apiKeyRepository, objectMapper);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }
}
