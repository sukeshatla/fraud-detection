package com.fraudplatform.scoring.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The scoring service's view of a transaction (its own model, decoupled from the wire contract).
 *
 * @param metadata opaque transport metadata (trace context, request id) carried along so that
 *                 messages <i>caused</i> by this transaction can be correlated with it. Rules never
 *                 look at it.
 */
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
        Instant occurredAt,
        Map<String, String> metadata) {

    public Transaction(UUID eventId, String transactionId, String accountId, BigDecimal amount, String currency,
            String merchantId, String merchantCategoryCode, String country, String channel, Instant occurredAt) {
        this(eventId, transactionId, accountId, amount, currency, merchantId, merchantCategoryCode, country, channel,
                occurredAt, Map.of());
    }

    public Transaction withMetadata(Map<String, String> newMetadata) {
        return new Transaction(eventId, transactionId, accountId, amount, currency, merchantId, merchantCategoryCode,
                country, channel, occurredAt, newMetadata);
    }

    public Transaction {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
