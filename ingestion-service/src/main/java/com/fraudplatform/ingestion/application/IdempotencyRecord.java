package com.fraudplatform.ingestion.application;

/**
 * What the idempotency store remembers about a key.
 *
 * @param status      IN_PROGRESS while the first request is publishing, COMPLETED once it succeeded
 * @param fingerprint request fingerprint, to detect key reuse with a different body
 * @param receipt     the original response; null while IN_PROGRESS
 */
public record IdempotencyRecord(Status status, String fingerprint, IngestionReceipt receipt) {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public static IdempotencyRecord inProgress(String fingerprint) {
        return new IdempotencyRecord(Status.IN_PROGRESS, fingerprint, null);
    }

    public static IdempotencyRecord completed(String fingerprint, IngestionReceipt receipt) {
        return new IdempotencyRecord(Status.COMPLETED, fingerprint, receipt);
    }
}
