# Feature 003 — Rule-based scoring engine

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 001 |
| Concepts | [Event-driven decoupling](../../docs/concepts/01-event-driven-kafka.md), [Idempotency](../../docs/concepts/12-idempotency-and-exactly-once.md), [Distributed caching](../../docs/concepts/02-distributed-caching-redis.md) |

## 1. Problem / motivation
Known fraud patterns (card testing, account takeover, geographic impossibility) are best caught by explainable deterministic rules. Analysts need to see *which* rule fired and *why*.

## 2. User stories
- **US-1** As a fraud analyst, I want each transaction scored by named rules with reasons, so that I can explain a decision.
- **US-2** As a rule author, I want to add a rule by adding one class, so that the engine never changes (open/closed principle).

## 3. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-003-01 | **Given** a `TransactionReceivedEvent` on `transactions.received.v1`, **when** scoring-service consumes it, **then** every enabled `FraudRule` is evaluated and a `RiskAssessment` with `ruleScore` 0–100 and the list of `RuleHit`s is produced. |
| AC-003-02 | **HighAmountRule:** amount ≥ configurable threshold (default 5,000 USD-equivalent) → hit, weight 30. |
| AC-003-03 | **VelocityRule:** more than 5 transactions for the same account in a sliding 60 s window → hit, weight 40. The window is held in a Redis sorted set (`ZADD`/`ZREMRANGEBYSCORE`/`ZCARD` in one Lua call). |
| AC-003-04 | **GeoVelocityRule ("impossible travel"):** a country different from the account's previous transaction within 1 h → hit, weight 35. |
| AC-003-05 | **HighRiskMccRule:** MCC in the high-risk list (e.g. 7995 gambling, 6051 quasi-cash) → hit, weight 20. |
| AC-003-06 | **CardTestingRule:** 3 or more transactions under 2.00 within 5 min → hit, weight 45. |
| AC-003-07 | The final rule score is `min(100, Σ weights)`. Decision: < 40 `APPROVE`, 40–74 `REVIEW`, ≥ 75 `DECLINE`. |
| AC-003-08 | **Given** the same `eventId` is delivered twice, **when** consumed, **then** it is scored once. The `processed:{eventId}` marker is written **after** the side effects (writing it first would turn a crash into silent data loss, i.e. at-most-once), and every side effect is itself idempotent. |
| AC-003-09 | **Given** a message that cannot be deserialised, **when** consumed, **then** it is sent to `transactions.received.v1.DLT` after zero retries (poison pill), and the partition keeps moving. |
| AC-003-10 | **Given** a score ≥ 40, **when** scoring completes, **then** a `FraudAlertEvent` is published to `fraud.alerts.v1` keyed by `accountId`. |

## 4. Non-functional requirements
- Listener concurrency equals the partition count. Rules are evaluated in parallel on virtual threads (Feature 009 measures this).
- Offsets are committed only after the side effects (manual ack, `AckMode.RECORD` or `BATCH`).

## 5. Out of scope
Rule authoring UI. Rules hot-reloaded from a DB.
