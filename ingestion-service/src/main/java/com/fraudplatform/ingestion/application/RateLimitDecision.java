package com.fraudplatform.ingestion.application;

import java.time.Duration;

/**
 * Outcome of a rate-limit check.
 *
 * @param limit      bucket capacity (advertised as {@code RateLimit-Limit})
 * @param remaining  whole tokens left after this request
 * @param retryAfter when a rejected client may try again; zero when allowed
 */
public record RateLimitDecision(boolean allowed, long limit, long remaining, Duration retryAfter) {

    public static RateLimitDecision allowed(long limit, long remaining) {
        return new RateLimitDecision(true, limit, remaining, Duration.ZERO);
    }

    public static RateLimitDecision rejected(long limit, Duration retryAfter) {
        return new RateLimitDecision(false, limit, 0, retryAfter);
    }
}
