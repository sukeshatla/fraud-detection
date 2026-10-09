package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port: distributed cache of account risk. Disposable by design: every method must
 * degrade gracefully (miss / empty / no-op) if the cache is unavailable.
 */
public interface HighRiskAccountCache {

    Optional<RiskStatus> get(String accountId);

    void put(RiskStatus status);

    void evict(String accountId);

    /** Which of these accounts are flagged? One round trip for a whole batch. */
    Set<String> flaggedAmong(Collection<String> accountIds);

    List<HighRiskAccount> listFlagged(int max);

    /** Cross-instance single-flight: returns a token if this caller should load the value. */
    Optional<String> tryAcquireLoadLock(String accountId);

    void releaseLoadLock(String accountId, String token);
}
