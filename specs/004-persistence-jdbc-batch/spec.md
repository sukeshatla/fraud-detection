# Feature 004 — Persistence & query performance

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 003 |
| Concepts | [JDBC batch processing](../../docs/concepts/04-jdbc-batch-processing.md), [N+1 & composite indexing](../../docs/concepts/03-n-plus-one-and-composite-indexing.md) |

## 1. Problem / motivation
At 2k TPS, inserting a transaction, its score and its rule hits row-by-row costs about 3 round-trips per event, roughly 6k statements per second. Batching turns that into a handful of round-trips per Kafka poll.

## 2. User stories
- **US-1** As an operator, I want scored transactions persisted with minimal DB load, so that the database isn't the bottleneck.
- **US-2** As an analyst, I want an account's history to load instantly, so that I can investigate quickly.

## 3. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-004-01 | The schema is managed by **Flyway** migrations (`V1__init.sql` …). The app never uses `ddl-auto`. |
| AC-004-02 | **Given** a Kafka poll of N records, **when** persisted, **then** `transaction`, `risk_score` and `rule_hit` rows are written with `JdbcTemplate.batchUpdate`, batch size 500, in **one DB transaction per poll**, with `reWriteBatchedInserts=true`. |
| AC-004-03 | **Given** a duplicate `transaction_id`, **when** the batch is inserted, **then** `ON CONFLICT (transaction_id) DO NOTHING` makes it a no-op and the batch does not fail. |
| AC-004-04 | Composite index `(account_id, occurred_at DESC)` exists. `EXPLAIN` of "last 50 txns for account" shows an Index Scan with no Sort node (asserted in an IT). |
| AC-004-05 | A benchmark IT records batch vs single-row insert throughput for 10k rows and documents the result in the concept doc. |
| AC-004-06 | Primary keys are **UUIDv7** (time-ordered), so B-tree inserts stay right-leaning. The concept doc explains why random UUIDv4 fragments the index. The transaction key is *deterministic* (timestamp = `occurredAt`, random bits = hash of `transactionId`), so a redelivered transaction maps to the same row. |
| AC-004-07 | `GET /api/v1/accounts/{accountId}/transactions?limit=20` returns the newest transactions with their risk score and decision in **one** SQL statement, served by the composite index. |

## 4. Non-functional requirements
- HikariCP pool size is derived from the formula in the concept doc (connections ≈ cores × 2 + spindles), not left at the default.

## 5. Out of scope
Table partitioning by month (documented as future work).
