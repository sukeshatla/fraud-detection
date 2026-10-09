# Tasks — Feature 004 Persistence & query performance

- [x] T1 — `UuidV7`: time-ordered, deterministic per name (AC-004-06)
- [x] T2 — Flyway `V1__scoring_schema.sql` with composite index and conflict keys (AC-004-01)
- [x] T3 — `AssessmentRepository` port + `JdbcAssessmentRepository` (batchUpdate × 3 in one transaction, ON CONFLICT DO NOTHING) (AC-004-02, 03)
- [x] T4 — Use case `scoreBatch`: dedupe in batch, score, persist once, alert, mark processed
- [x] T5 — Batch listener with `BatchListenerFailedException` for poison pills
- [x] T6 — `QueryPlanIT`: EXPLAIN proves Index Scan, no Sort (AC-004-04)
- [x] T7 — `BatchInsertBenchmarkIT` (AC-004-05)
- [x] T8 — `GET /api/v1/accounts/{id}/transactions` (AC-004-07)
- [x] T9 — Hikari sizing, docker-compose wiring, docs
