package com.fraudplatform.ingestion.domain;

/** Thrown when a domain invariant is violated. */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
