package com.fraudplatform.scoring.application;

import java.util.List;

/** Use case: an analyst looks at an account's recent transactions. */
public class AccountHistoryService {

    static final int MAX_LIMIT = 100;

    private final TransactionHistoryQuery query;

    public AccountHistoryService(TransactionHistoryQuery query) {
        this.query = query;
    }

    /** {@code limit} is clamped to 1..{@value #MAX_LIMIT}: never let a client ask for an unbounded scan. */
    public List<TransactionHistoryEntry> recent(String accountId, int limit) {
        return query.recentForAccount(accountId, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
