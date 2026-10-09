-- Canonical outbox table. Each service copies this into its OWN Flyway migration (database per service).
CREATE TABLE outbox (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- relay order = insertion order
    topic       varchar(255) NOT NULL,
    message_key varchar(255),
    payload     text         NOT NULL,
    headers     jsonb        NOT NULL DEFAULT '{}',
    created_at  timestamptz  NOT NULL DEFAULT now()
);
