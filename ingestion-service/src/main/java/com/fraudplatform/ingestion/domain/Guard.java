package com.fraudplatform.ingestion.domain;

/** Small invariant helpers so domain constructors stay readable. */
final class Guard {

    private Guard() {}

    static <T> T required(T value, String name) {
        if (value == null) {
            throw new DomainValidationException(name + " is required");
        }
        return value;
    }

    static String notBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(name + " must not be blank");
        }
        return value;
    }
}
