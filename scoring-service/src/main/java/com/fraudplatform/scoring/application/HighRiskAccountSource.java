package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.HighRiskAccount;
import java.time.Instant;
import java.util.Optional;

/** Outbound port: the source of truth for account risk (PostgreSQL). */
public interface HighRiskAccountSource {

    /** The most recent active flag: a DECLINE within the flag window, newer than any clearance. */
    Optional<HighRiskAccount> findActiveFlag(String accountId);

    void recordClearance(String accountId, Instant at);
}
