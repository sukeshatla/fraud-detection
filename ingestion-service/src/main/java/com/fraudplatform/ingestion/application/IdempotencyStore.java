package com.fraudplatform.ingestion.application;

import java.util.Optional;

/** Outbound port: remembers Idempotency-Keys across all ingestion instances. */
public interface IdempotencyStore {

    /**
     * Atomically claims {@code key} for this request.
     *
     * @return empty if this caller now owns the key; otherwise the record left by an earlier request
     */
    Optional<IdempotencyRecord> claim(String key, String fingerprint);

    /** Stores the final result so retries can replay it. */
    void complete(String key, IdempotencyRecord record);

    /** Forgets the key after a failure, so the client may retry. */
    void release(String key);
}
