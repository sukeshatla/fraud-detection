package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;

/** Outbound port: sliding-window history per account. Recording must be idempotent per eventId. */
public interface AccountActivityStore {

    /** Records {@code transaction} and returns the account's activity including it. */
    AccountActivity recordAndGet(Transaction transaction);
}
