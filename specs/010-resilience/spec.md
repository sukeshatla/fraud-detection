# Feature 010 — Resilience

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 003, 004 |
| Concepts | [Resilience patterns](../../docs/concepts/13-resilience-patterns.md), [Idempotency & exactly-once](../../docs/concepts/12-idempotency-and-exactly-once.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-010-01 | **Retries with backoff:** transient consumer failures retry 3× with exponential backoff **and jitter** (`DefaultErrorHandler` + a `JitteredExponentialBackOff`, since Spring's built-in backoff has no jitter). Then the record goes to the DLT with failure headers. |
| AC-010-02 | **Non-retryable** exceptions (validation, deserialisation) skip retries and go straight to the DLT. |
| AC-010-03 | **DLT replay:** an admin endpoint re-publishes DLT records to the main topic after a fix. |
| AC-010-04 | **Transactional outbox:** scoring writes the score **and** an `outbox` row (the alert) in one DB transaction, and alert-service does the same for resolution events. A relay publishes outbox rows to Kafka in insertion order and deletes them once acknowledged. Exactly one relay is active per service (PostgreSQL advisory lock), which preserves order, and standbys take over automatically. Tests prove: Kafka down after commit → nothing lost, delivered once it recovers; rollback → nothing published; two relays → no duplicates. |
| AC-010-05 | **Circuit breaker** (Resilience4j) around the ML scorer port: it opens after a 50% failure rate over 20 calls. While open, scoring is rules-only. It goes half-open after 10 s. |
| AC-010-06 | **Timeouts** are explicit on every outbound call (Redis, Kafka, DB, ML). No infinite defaults. |
| AC-010-07 | **Graceful shutdown:** `server.shutdown=graceful` with a bounded `timeout-per-shutdown-phase`. Kafka listener containers finish the current poll before stopping (Spring Kafka semantics). The outbox relay drains its in-flight batch on stop (tested), and SSE streams are completed first (Feature 008). |
