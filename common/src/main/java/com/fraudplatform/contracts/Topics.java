package com.fraudplatform.contracts;

/** Kafka topic names. Versioned so that breaking schema changes get a new topic. */
public final class Topics {

    public static final String TRANSACTIONS_RECEIVED = "transactions.received.v1";
    public static final String TRANSACTIONS_SCORED = "transactions.scored.v1";
    public static final String FRAUD_ALERTS = "fraud.alerts.v1";

    private Topics() {}
}
