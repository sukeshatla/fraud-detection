package com.fraudplatform.scoring.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The scoring service's view of a transaction (its own model, decoupled from the wire contract). */
public record Transaction(
        UUID eventId,
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategoryCode,
        String country,
        String channel,
        Instant occurredAt) {

    public Transaction {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
