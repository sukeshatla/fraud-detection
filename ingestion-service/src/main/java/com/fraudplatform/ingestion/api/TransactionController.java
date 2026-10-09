package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.application.IngestTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    private final IngestTransactionService service;

    TransactionController(IngestTransactionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    TransactionAcceptedResponse submit(@Valid @RequestBody TransactionRequest request) {
        return TransactionAcceptedResponse.from(service.ingest(request.toDomain()));
    }
}
