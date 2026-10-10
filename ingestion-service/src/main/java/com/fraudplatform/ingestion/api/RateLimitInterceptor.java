package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import com.fraudplatform.security.KeycloakJwtAuthenticationConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces per-client quotas before the controller runs and advertises the quota on every response
 * (IETF draft {@code RateLimit-*} headers).
 *
 * <p>The client is the OAuth2 client the access token was issued to ({@code azp}), never a
 * request header: headers can be spoofed to spend someone else's quota (Feature 015).
 */
class RateLimitInterceptor implements HandlerInterceptor {

    static final String ANONYMOUS = "anonymous";

    private final RateLimiter rateLimiter;

    RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientId = request.getUserPrincipal() instanceof JwtAuthenticationToken token
                ? KeycloakJwtAuthenticationConverter.clientId(token.getToken())
                : ANONYMOUS;
        RateLimitDecision decision = rateLimiter.tryAcquire(clientId);

        response.setHeader("RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision);
        }
        return true;
    }
}
