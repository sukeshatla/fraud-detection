package com.fraudplatform.ingestion.domain;

import java.time.Instant;

/** A payment transaction as submitted by a gateway. Immutable and always valid once constructed. */
public record Transaction(
        String transactionId,
        String accountId,
        Money amount,
        String merchantId,
        String merchantCategoryCode,
        String country,
        Channel channel,
        Instant occurredAt) {

    public Transaction {
        Guard.notBlank(transactionId, "transactionId");
        Guard.notBlank(accountId, "accountId");
        Guard.required(amount, "amount");
        Guard.notBlank(merchantId, "merchantId");
        Guard.notBlank(merchantCategoryCode, "merchantCategoryCode");
        Guard.notBlank(country, "country");
        Guard.required(channel, "channel");
        Guard.required(occurredAt, "occurredAt");
    }
}
