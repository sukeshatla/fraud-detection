package com.fraudplatform.testing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Real, signed JWTs for tests, shaped like Keycloak's ({@code realm_access.roles}, {@code azp},
 * {@code preferred_username}). Services' integration tests replace the JWKS-backed decoder with
 * {@link #decoder()}, so every request goes through the real Spring Security filter chain.
 */
public final class TestJwts {

    public static final String ISSUER = "http://localhost:8080/realms/fraud";

    private static final SecretKey KEY = new SecretKeySpec(
            "test-only-hmac-key-never-used-outside-tests-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static final NimbusJwtEncoder ENCODER = NimbusJwtEncoder.withSecretKey(KEY).build();

    private TestJwts() {}

    public static JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** A person logged in through the dashboard. */
    public static String user(String username, String... realmRoles) {
        return mint(Map.of("preferred_username", username, "azp", "fraud-dashboard",
                "realm_access", Map.of("roles", List.of(realmRoles))));
    }

    /** A machine client using the client-credentials grant (e.g. a payment gateway). */
    public static String client(String clientId, String... realmRoles) {
        return mint(Map.of("preferred_username", "service-account-" + clientId, "azp", clientId,
                "realm_access", Map.of("roles", List.of(realmRoles))));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String mint(Map<String, Object> claims) {
        Instant now = Instant.now();
        JwtClaimsSet set = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("sub-" + claims.get("azp"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claims(c -> c.putAll(claims))
                .build();
        return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), set)).getTokenValue();
    }
}
