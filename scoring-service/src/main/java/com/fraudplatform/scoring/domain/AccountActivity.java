package com.fraudplatform.scoring.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Recent behaviour of an account, <b>including</b> the transaction being scored. Fetched once before
 * the rules run, so rules stay pure functions.
 *
 * @param txCountLast60s     transactions in the 60 s before (and including) this one
 * @param txCountLast1h      … in the last hour
 * @param txCountLast24h     … in the last 24 h
 * @param smallTxCountLast5m transactions under the card-testing threshold in the last 5 min
 * @param previousCountry    country of the account's previous transaction, or null if none
 * @param previousOccurredAt time of the previous transaction, or null if none
 * @param knownHighRisk      the account is flagged in the high-risk cache (Feature 005)
 */
public record AccountActivity(
        int txCountLast60s,
        int txCountLast1h,
        int txCountLast24h,
        int smallTxCountLast5m,
        String previousCountry,
        Instant previousOccurredAt,
        boolean knownHighRisk) {

    /** Activity as read from the sliding windows, before the risk-cache lookup. */
    public AccountActivity(int txCountLast60s, int txCountLast1h, int txCountLast24h, int smallTxCountLast5m,
            String previousCountry, Instant previousOccurredAt) {
        this(txCountLast60s, txCountLast1h, txCountLast24h, smallTxCountLast5m, previousCountry, previousOccurredAt, false);
    }

    /** No history at all: the account's first transaction. */
    public static AccountActivity none() {
        return new AccountActivity(1, 1, 1, 0, null, null, false);
    }

    public AccountActivity withKnownHighRisk(boolean flagged) {
        return new AccountActivity(txCountLast60s, txCountLast1h, txCountLast24h, smallTxCountLast5m,
                previousCountry, previousOccurredAt, flagged);
    }

    public Optional<String> previousCountryIfAny() {
        return Optional.ofNullable(previousCountry);
    }
}
