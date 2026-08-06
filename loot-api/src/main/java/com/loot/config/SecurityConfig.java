package com.loot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal interim config: webhook endpoints and /actuator/health must be
 * reachable without auth (Daraja/Flutterwave can't authenticate as us), and
 * this is a stateless REST API so CSRF protection (meant for browser
 * session/cookie auth) doesn't apply. API key auth for the rest of the API
 * lands in t42; this only exists so Spring Security's default
 * "authenticate everything" auto-configuration doesn't 401 the gateways'
 * callbacks in the meantime.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/webhooks/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
