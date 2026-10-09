package com.fraudplatform.ingestion.application;

/** A request with the same Idempotency-Key is still being processed. */
public class IdempotentRequestInProgressException extends RuntimeException {

    public IdempotentRequestInProgressException(String key) {
        super("A request with Idempotency-Key '%s' is still in progress".formatted(key));
    }
}
