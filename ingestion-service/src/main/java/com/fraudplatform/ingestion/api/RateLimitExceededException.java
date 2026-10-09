package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.RateLimitDecision;

/** Raised by {@link RateLimitInterceptor}; rendered as 429 by {@link GlobalExceptionHandler}. */
class RateLimitExceededException extends RuntimeException {

    private final transient RateLimitDecision decision;

    RateLimitExceededException(RateLimitDecision decision) {
        super("Rate limit exceeded");
        this.decision = decision;
    }

    RateLimitDecision decision() {
        return decision;
    }
}
