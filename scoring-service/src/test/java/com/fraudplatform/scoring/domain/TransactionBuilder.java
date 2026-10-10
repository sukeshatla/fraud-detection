package com.fraudplatform.scoring.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Test data builder: a low-risk transaction by default; tests override only what they care about. */
public final class TransactionBuilder {

    public static final Instant NOW = Instant.parse("2026-10-09T18:15:30Z");

    private UUID eventId = UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11");
    private String transactionId = "txn-1";
    private String accountId = "acc-1001";
    private BigDecimal amount = new BigDecimal("42.00");
    private String currency = "USD";
    private String merchantId = "m-5411";
    private String mcc = "5411";
    private String country = "US";
    private String channel = "CARD_PRESENT";
    private Instant occurredAt = NOW;

    public static TransactionBuilder aTransaction() {
        return new TransactionBuilder();
    }

    public TransactionBuilder eventId(UUID v) {
        this.eventId = v;
        return this;
    }
    public TransactionBuilder transactionId(String v) {
        this.transactionId = v;
        return this;
    }
    public TransactionBuilder accountId(String v) {
        this.accountId = v;
        return this;
    }
    public TransactionBuilder amount(String v) {
        this.amount = new BigDecimal(v);
        return this;
    }
    public TransactionBuilder currency(String v) {
        this.currency = v;
        return this;
    }
    public TransactionBuilder mcc(String v) {
        this.mcc = v;
        return this;
    }
    public TransactionBuilder country(String v) {
        this.country = v;
        return this;
    }
    public TransactionBuilder channel(String v) {
        this.channel = v;
        return this;
    }
    public TransactionBuilder occurredAt(Instant v) {
        this.occurredAt = v;
        return this;
    }

    public Transaction build() {
        return new Transaction(eventId, transactionId, accountId, amount, currency, merchantId, mcc, country, channel, occurredAt);
    }
}
