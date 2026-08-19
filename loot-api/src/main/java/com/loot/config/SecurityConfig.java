package com.loot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.repository.ApiKeyRepository;
import com.loot.security.ApiKeyAuthFilter;
import com.loot.security.DarajaCallbackIpFilter;
import com.loot.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * app.security.require-https gates requiresSecure() so plain local dev
 * still works - it's turned on in the staging/prod profiles, which also
 * set server.forward-headers-strategy=framework so request.isSecure()
 * reflects the X-Forwarded-Proto set by the platform's TLS-terminating
 * proxy rather than always reading as plain HTTP.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter,
            RateLimitFilter rateLimitFilter,
            DarajaCallbackIpFilter darajaCallbackIpFilter,
            @Value("${app.security.require-https:false}") boolean requireHttps) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/webhooks/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)))
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(darajaCallbackIpFilter, ApiKeyAuthFilter.class)
            .addFilterAfter(rateLimitFilter, ApiKeyAuthFilter.class);

        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

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

    @Bean
    public DarajaCallbackIpFilter darajaCallbackIpFilter(
            @Value("${daraja.callback-allowed-ips:}") String allowedIpsCsv) {
        return new DarajaCallbackIpFilter(allowedIpsCsv);
    }
}
