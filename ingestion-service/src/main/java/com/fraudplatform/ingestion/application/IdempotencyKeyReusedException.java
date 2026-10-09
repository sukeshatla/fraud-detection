package com.fraudplatform.ingestion.application;

/** The Idempotency-Key was already used for a different request body. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '%s' was already used with a different request body".formatted(key));
    }
}
