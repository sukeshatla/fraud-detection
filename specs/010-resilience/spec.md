# Feature 010 — Resilience

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 003, 004 |
| Concepts | [Resilience patterns](../../docs/concepts/13-resilience-patterns.md), [Idempotency & exactly-once](../../docs/concepts/12-idempotency-and-exactly-once.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-010-01 | **Retries with backoff:** transient consumer failures retry 3× with exponential backoff and jitter (`DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries`). Then the record goes to the DLT with failure headers. |
| AC-010-02 | **Non-retryable** exceptions (validation, deserialisation) skip retries and go straight to the DLT. |
| AC-010-03 | **DLT replay:** an admin endpoint re-publishes DLT records to the main topic after a fix. |
| AC-010-04 | **Transactional outbox:** scoring writes the score **and** an `outbox` row in one DB transaction. A relay publishes outbox rows to Kafka and marks them sent. This removes the dual-write problem. A test kills the publisher between the DB commit and the send and proves the event is still delivered. |
| AC-010-05 | **Circuit breaker** (Resilience4j) around the ML scorer port: it opens after a 50% failure rate over 20 calls. While open, scoring is rules-only. It goes half-open after 10 s. |
| AC-010-06 | **Timeouts** are explicit on every outbound call (Redis, Kafka, DB, ML). No infinite defaults. |
| AC-010-07 | **Graceful shutdown:** in-flight HTTP requests complete, Kafka consumers commit, and the outbox relay stops cleanly (tested with SIGTERM in an IT). |
