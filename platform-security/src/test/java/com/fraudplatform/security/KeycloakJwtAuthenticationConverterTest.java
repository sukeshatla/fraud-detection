package com.fraudplatform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    @DisplayName("AC-015-02: realm roles become ROLE_ authorities; a human's name is preferred_username")
    void humanUser() {
        var auth = converter.convert(jwt(Map.of("preferred_username", "supervisor", "azp", "fraud-dashboard",
                "realm_access", Map.of("roles", List.of("ANALYST", "SUPERVISOR")))));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ANALYST", "ROLE_SUPERVISOR");
        assertThat(auth.getName()).isEqualTo("supervisor");
    }

    @Test
    @DisplayName("AC-015-01: a machine client's name is its client id (azp), never the synthetic service-account user")
    void machineClient() {
        var token = jwt(Map.of("preferred_username", "service-account-payment-gateway", "azp", "payment-gateway",
                "realm_access", Map.of("roles", List.of("INGEST"))));

        assertThat(converter.convert(token).getName()).isEqualTo("payment-gateway");
        assertThat(KeycloakJwtAuthenticationConverter.clientId(token)).isEqualTo("payment-gateway");
    }

    @Test
    void noRolesNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of("azp", "x"))).getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("sub-1").claims(c -> c.putAll(claims))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
