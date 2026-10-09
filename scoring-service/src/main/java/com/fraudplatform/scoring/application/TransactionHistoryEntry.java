package com.fraudplatform.scoring.application;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for an account's transaction history (one row per transaction, score included). */
public record TransactionHistoryEntry(
        String transactionId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategoryCode,
        String country,
        String channel,
        Instant occurredAt,
        int riskScore,
        String decision) {}
