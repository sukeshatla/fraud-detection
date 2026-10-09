package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.IngestionReceipt;
import java.time.Instant;
import java.util.UUID;

public record TransactionAcceptedResponse(String transactionId, UUID eventId, String status, Instant receivedAt) {

    static TransactionAcceptedResponse from(IngestionReceipt receipt) {
        return new TransactionAcceptedResponse(
                receipt.transactionId(), receipt.eventId(), "ACCEPTED", receipt.receivedAt());
    }
}
