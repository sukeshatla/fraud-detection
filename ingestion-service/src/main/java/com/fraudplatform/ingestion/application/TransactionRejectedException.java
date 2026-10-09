package com.fraudplatform.ingestion.application;

/** The transaction is well-formed but violates a business rule. */
public class TransactionRejectedException extends RuntimeException {

    public TransactionRejectedException(String message) {
        super(message);
    }
}
