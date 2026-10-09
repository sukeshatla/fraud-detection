package com.fraudplatform.ingestion.application;

import java.time.Instant;
import java.util.UUID;

/** Proof of acceptance returned to the caller. */
public record IngestionReceipt(String transactionId, UUID eventId, Instant receivedAt) {}
