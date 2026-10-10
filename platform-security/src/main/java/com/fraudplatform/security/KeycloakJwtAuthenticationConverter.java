package com.fraudplatform.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps a Keycloak access token to Spring Security:
 * <ul>
 *   <li>{@code realm_access.roles: [ANALYST]} → authority {@code ROLE_ANALYST}
 *   <li>principal name = {@code preferred_username} for people, or the client id ({@code azp})
 *       for machine clients (client-credentials tokens have no human user)
 * </ul>
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), principalName(jwt));
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /** Human: preferred_username. Machine (client credentials): the client id. */
    public static String principalName(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                .filter(name -> !name.startsWith("service-account-"))
                .orElseGet(() -> Optional.ofNullable(jwt.getClaimAsString("azp")).orElse(jwt.getSubject()));
    }

    /** The OAuth2 client the token was issued to: the stable identity for per-client quotas. */
    public static String clientId(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsString("azp")).orElse(jwt.getSubject());
    }
}
