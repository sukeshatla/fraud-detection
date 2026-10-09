package com.fraudplatform.ingestion.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof of acceptance returned to the caller.
 *
 * @param replayed true when this is the stored result of an earlier request with the same Idempotency-Key
 */
public record IngestionReceipt(String transactionId, UUID eventId, Instant receivedAt, boolean replayed) {

    IngestionReceipt asReplay() {
        return new IngestionReceipt(transactionId, eventId, receivedAt, true);
    }
}
