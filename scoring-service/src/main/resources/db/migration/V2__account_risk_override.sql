-- Analysts can clear an account (false positive). The clearance is part of the source of truth,
-- so a cache reload after eviction does not re-flag the account from an older DECLINE.
CREATE TABLE account_risk_override (
    account_id varchar(64) PRIMARY KEY,
    cleared_at timestamptz NOT NULL
);
