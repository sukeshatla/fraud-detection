# Tasks — Feature 001 Transaction ingestion API

Each task: 🔴 test → 🟢 code → ♻️ refactor.

- [x] T1 — `TransactionReceivedEvent` contract + golden-file contract test (`common`) (AC-001-02)
- [x] T2 — Domain `Money`, `Channel`, `Transaction` with invariants + unit tests
- [x] T3 — `IngestTransactionService`: stamps eventId/receivedAt, publishes (AC-001-01)
- [x] T4 — Clock-skew rule rejects future `occurredAt` > 5 min (AC-001-05)
- [x] T5 — `TransactionController` + DTO validation → 202 / 400 with all field errors (AC-001-01, AC-001-03)
- [x] T6 — `@IsoCurrency`, `@IsoCountry` constraints (AC-001-07)
- [x] T7 — `GlobalExceptionHandler`: malformed JSON → 400, publish failure → 503 + Retry-After (AC-001-04, AC-001-06)
- [x] T8 — `KafkaTransactionPublisher`: topic, key, headers, timeout → `EventPublishingException` (AC-001-02, AC-001-06)
- [x] T9 — Producer config (acks=all, idempotence, lz4) + topic declaration
- [x] T10 — `TransactionIngestionIT` with Testcontainers Kafka: end-to-end publish + same-partition ordering (AC-001-02, AC-001-08)
- [x] T11 — ArchUnit layering test passes
- [x] T12 — Update concept docs 01 and 07 and the roadmap status
