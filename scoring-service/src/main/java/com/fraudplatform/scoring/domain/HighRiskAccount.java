package com.fraudplatform.scoring.domain;

import java.time.Instant;

/** An account recently declined for fraud. Subsequent transactions are treated with suspicion. */
public record HighRiskAccount(String accountId, int riskScore, String reason, Instant flaggedAt) {}
