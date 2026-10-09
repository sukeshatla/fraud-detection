-- Scoring service schema (owned exclusively by scoring-service: database-per-service).

CREATE TABLE transaction (
    id                     uuid           PRIMARY KEY,          -- deterministic UUIDv7: time-ordered inserts
    transaction_id         varchar(64)    NOT NULL,
    event_id               uuid           NOT NULL,
    account_id             varchar(64)    NOT NULL,
    amount                 numeric(19, 4) NOT NULL,
    currency               char(3)        NOT NULL,
    merchant_id            varchar(64)    NOT NULL,
    merchant_category_code char(4)        NOT NULL,
    country                char(2)        NOT NULL,
    channel                varchar(20)    NOT NULL,
    occurred_at            timestamptz    NOT NULL,
    created_at             timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_transaction_id UNIQUE (transaction_id)   -- idempotent replays
);

-- Account history + velocity lookups: equality column first, then the sort column in query order.
-- Serves  WHERE account_id = ? ORDER BY occurred_at DESC LIMIT ?  with no Sort node.
CREATE INDEX ix_transaction_account_occurred ON transaction (account_id, occurred_at DESC);

CREATE TABLE risk_score (
    transaction_pk uuid         PRIMARY KEY REFERENCES transaction (id),
    rule_score     smallint     NOT NULL CHECK (rule_score BETWEEN 0 AND 100),
    ml_probability numeric(5, 4),                                        -- Feature 006
    model_version  varchar(32),                                          -- Feature 006
    risk_score     smallint     NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    decision       varchar(10)  NOT NULL CHECK (decision IN ('APPROVE', 'REVIEW', 'DECLINE')),
    scored_at      timestamptz  NOT NULL
);

CREATE TABLE rule_hit (
    id             bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_pk uuid         NOT NULL REFERENCES transaction (id),
    rule_code      varchar(32)  NOT NULL,
    weight         smallint     NOT NULL,
    reason         varchar(255) NOT NULL,
    CONSTRAINT uq_rule_hit UNIQUE (transaction_pk, rule_code)  -- replay-safe; also indexes the FK
);
