package com.fraudplatform.ingestion.infrastructure.redis;

/**
 * A client's token-bucket parameters.
 *
 * @param capacity        maximum burst (bucket size)
 * @param refillPerSecond sustained requests per second
 */
public record Quota(long capacity, double refillPerSecond) {

    public Quota {
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refillPerSecond must be positive");
        }
    }
}
