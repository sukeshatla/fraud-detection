-- Alert service schema (owned exclusively by alert-service).

CREATE TABLE alert (
    id                     uuid           PRIMARY KEY,      -- = FraudAlertEvent.alertEventId (deterministic)
    transaction_id         varchar(64)    NOT NULL,
    account_id             varchar(64)    NOT NULL,
    amount                 numeric(19, 4) NOT NULL,
    currency               varchar(3)     NOT NULL,
    merchant_id            varchar(64)    NOT NULL,
    merchant_category_code varchar(4)     NOT NULL,
    country                varchar(2)     NOT NULL,
    channel                varchar(20)    NOT NULL,
    occurred_at            timestamptz    NOT NULL,
    rule_score             integer        NOT NULL,
    risk_score             integer        NOT NULL,
    ml_probability         numeric(5, 4),
    model_version          varchar(32),
    decision               varchar(10)    NOT NULL,
    severity               varchar(10)    NOT NULL,
    status                 varchar(20)    NOT NULL,
    version                bigint         NOT NULL DEFAULT 0,   -- optimistic locking (JPA @Version)
    created_at             timestamptz    NOT NULL,
    updated_at             timestamptz    NOT NULL,
    CONSTRAINT uq_alert_transaction UNIQUE (transaction_id)      -- one alert per transaction, replay-safe
);

-- Analyst queue: WHERE status = ? AND severity = ? ORDER BY created_at DESC, id DESC
-- Equality columns first, then the sort columns in query order; id breaks ties for keyset paging.
CREATE INDEX ix_alert_queue ON alert (status, severity, created_at DESC, id DESC);

-- Hot path: the OPEN queue. Resolved alerts (the vast majority over time) are not in this index at all.
CREATE INDEX ix_alert_open ON alert (created_at DESC, id DESC) WHERE status = 'OPEN';

CREATE INDEX ix_alert_account ON alert (account_id, created_at DESC);

CREATE TABLE alert_rule_hit (
    id       bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alert_id uuid         NOT NULL REFERENCES alert (id),
    code     varchar(32)  NOT NULL,
    weight   integer      NOT NULL,
    reason   varchar(255) NOT NULL,
    CONSTRAINT uq_alert_rule_hit UNIQUE (alert_id, code)   -- also serves "hits WHERE alert_id IN (…)"
);

-- Audit trail: who changed what, when. Written in the same transaction as the status change.
CREATE TABLE alert_event (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alert_id    uuid        NOT NULL REFERENCES alert (id),
    from_status varchar(20) NOT NULL,
    to_status   varchar(20) NOT NULL,
    actor       varchar(64) NOT NULL,
    changed_at  timestamptz NOT NULL
);
CREATE INDEX ix_alert_event_alert ON alert_event (alert_id, changed_at);
