package com.fraudplatform.ingestion.domain;

import java.time.Instant;
import java.util.UUID;

/** A transaction that ingestion has accepted, stamped with its event identity. */
public record ReceivedTransaction(UUID eventId, Instant receivedAt, Transaction transaction) {

    public ReceivedTransaction {
        Guard.required(eventId, "eventId");
        Guard.required(receivedAt, "receivedAt");
        Guard.required(transaction, "transaction");
    }
}
