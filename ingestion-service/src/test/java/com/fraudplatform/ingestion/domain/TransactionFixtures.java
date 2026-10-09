package com.fraudplatform.ingestion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/** Test data builder: a valid transaction by default, overridable per test. */
public final class TransactionFixtures {

    public static final Instant OCCURRED_AT = Instant.parse("2026-10-09T18:15:30Z");

    private TransactionFixtures() {}

    public static Transaction aTransaction() {
        return aTransactionOccurredAt(OCCURRED_AT);
    }

    public static Transaction aTransactionOccurredAt(Instant occurredAt) {
        return new Transaction(
                "txn-7f3a9c",
                "acc-1001",
                new Money(new BigDecimal("249.99"), Currency.getInstance("USD")),
                "m-5541",
                "5732",
                "US",
                Channel.CARD_NOT_PRESENT,
                occurredAt);
    }
}
