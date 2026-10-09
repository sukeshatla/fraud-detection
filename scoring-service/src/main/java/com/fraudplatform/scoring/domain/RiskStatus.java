package com.fraudplatform.scoring.domain;

/**
 * Cached answer to "is this account high-risk?". Both answers are cached: {@link Clear} is the
 * negative-cache entry that stops repeated lookups of clean accounts from reaching the database.
 */
public sealed interface RiskStatus permits RiskStatus.Flagged, RiskStatus.Clear {

    String accountId();

    default boolean isHighRisk() {
        return this instanceof Flagged;
    }

    record Flagged(HighRiskAccount account) implements RiskStatus {
        @Override
        public String accountId() {
            return account.accountId();
        }
    }

    record Clear(String accountId) implements RiskStatus {}
}
