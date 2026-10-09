package com.fraudplatform.ingestion.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

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

    /**
     * SHA-256 over the canonical field values. Two requests carrying the same Idempotency-Key must
     * have the same fingerprint, otherwise the key is being reused for a different transaction.
     * Amounts are normalised ({@code 249.990} equals {@code 249.99}).
     */
    public String fingerprint() {
        String canonical = String.join("|",
                transactionId,
                accountId,
                amount.amount().stripTrailingZeros().toPlainString(),
                amount.currency().getCurrencyCode(),
                merchantId,
                merchantCategoryCode,
                country,
                channel.name(),
                occurredAt.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", e);
        }
    }
}
