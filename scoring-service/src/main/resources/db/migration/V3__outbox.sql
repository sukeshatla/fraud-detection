-- Transactional outbox (Feature 010): alerts are written here in the same transaction as the
-- assessments, then relayed to Kafka. Canonical DDL: platform-messaging/.../outbox/outbox.sql
CREATE TABLE outbox (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic       varchar(255) NOT NULL,
    message_key varchar(255),
    payload     text         NOT NULL,
    headers     jsonb        NOT NULL DEFAULT '{}',
    created_at  timestamptz  NOT NULL DEFAULT now()
);
