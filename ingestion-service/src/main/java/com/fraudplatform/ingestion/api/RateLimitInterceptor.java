package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces per-client quotas before the controller runs and advertises the quota on every response
 * (IETF draft {@code RateLimit-*} headers).
 *
 * <p>The client is identified by {@code X-Client-Id} for now. Feature 015 derives it from the
 * authenticated token, because a header alone can be spoofed.
 */
class RateLimitInterceptor implements HandlerInterceptor {

    static final String CLIENT_ID_HEADER = "X-Client-Id";
    static final String ANONYMOUS = "anonymous";

    private final RateLimiter rateLimiter;

    RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        RateLimitDecision decision = rateLimiter.tryAcquire(clientId == null || clientId.isBlank() ? ANONYMOUS : clientId);

        response.setHeader("RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision);
        }
        return true;
    }
}
