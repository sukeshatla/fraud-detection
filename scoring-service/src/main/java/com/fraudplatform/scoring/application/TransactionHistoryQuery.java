package com.fraudplatform.scoring.application;

import java.util.List;

/** Outbound port: newest-first transaction history for one account. */
public interface TransactionHistoryQuery {

    List<TransactionHistoryEntry> recentForAccount(String accountId, int limit);
}
