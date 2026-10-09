package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.IngestTransactionService;
import com.fraudplatform.ingestion.application.IngestionReceipt;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Inbound adapter. Returns {@code 202 Accepted}, not {@code 201}: the transaction is durably queued
 * for scoring, but no fraud decision exists yet.
 *
 * <p>Runs on a virtual thread ({@code spring.threads.virtual.enabled}), so blocking until Kafka
 * acknowledges costs a few hundred bytes of heap instead of a whole platform thread.
 */
@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    private static final int MAX_KEY_LENGTH = 255;

    private final IngestTransactionService service;

    TransactionController(IngestTransactionService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TransactionAcceptedResponse> submit(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    IDEMPOTENCY_KEY + " must be 1-" + MAX_KEY_LENGTH + " characters");
        }

        IngestionReceipt receipt = service.ingest(request.toDomain(), idempotencyKey);

        ResponseEntity.BodyBuilder response = ResponseEntity.accepted();
        if (receipt.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(TransactionAcceptedResponse.from(receipt));
    }
}
