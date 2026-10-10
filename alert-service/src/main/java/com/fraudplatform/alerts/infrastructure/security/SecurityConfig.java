package com.fraudplatform.alerts.infrastructure.security;

import com.fraudplatform.security.KeycloakJwtAuthenticationConverter;
import com.fraudplatform.security.StreamTokenResolver;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The analyst queue: analysts and supervisors read and triage; closing an alert (a terminal status)
 * is a supervisor decision, checked in the controller because it depends on the request body.
 * The SSE stream is the only endpoint that accepts {@code ?access_token=} (EventSource can't send headers).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    public static final String STREAM_PATH = "/api/v1/alerts/stream";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("OPS")
                        .requestMatchers(HttpMethod.GET, "/api/v1/alerts", "/api/v1/alerts/**").hasAnyRole("ANALYST", "SUPERVISOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/alerts/*").hasAnyRole("ANALYST", "SUPERVISOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(new StreamTokenResolver(Set.of(STREAM_PATH)))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API: no cookies → no CSRF
                .cors(Customizer.withDefaults());
        return http.build();
    }

    /** Browsers may only call this API from the dashboard's origin (AC-015-05). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${fraud.security.cors.allowed-origins:http://localhost:8080}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "PATCH"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
