package com.fraudplatform.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@link com.fraudplatform.contracts.Topics#TRANSACTIONS_RECEIVED} once a transaction
 * has been validated and accepted by ingestion. Record key: {@code accountId}.
 *
 * <p><b>Evolution rules for v1:</b> only add optional fields; never rename, remove or change the
 * type of an existing field. Breaking changes go to {@code transactions.received.v2}.
 *
 * @param schemaVersion        payload version, always {@link #SCHEMA_VERSION} on this topic
 * @param eventId              unique per event; consumers dedupe on it (at-least-once delivery)
 * @param transactionId        client-supplied business identifier
 * @param accountId            opaque account identifier (never a PAN)
 * @param amount               positive amount in {@code currency} units
 * @param currency             ISO-4217 code
 * @param merchantId           merchant identifier
 * @param merchantCategoryCode ISO-18245 MCC, 4 digits
 * @param country              ISO-3166 alpha-2 country where the transaction happened
 * @param channel              CARD_PRESENT | CARD_NOT_PRESENT | CONTACTLESS | ATM
 * @param occurredAt           when the transaction happened at the merchant
 * @param receivedAt           when ingestion accepted it
 */
public record TransactionReceivedEvent(
        int schemaVersion,
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
        Instant receivedAt) {

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE = "TransactionReceived";
}
