# Tasks — Feature 010 Resilience

- [x] T1 — `platform-messaging` module + ADR-0007
- [x] T2 — `JitteredExponentialBackOff` + test; wire into both error handlers (AC-010-01)
- [x] T3 — `OutboxWriter` (must join a transaction), `OutboxRelay` (advisory lock, ordered, delete-after-ack), `OutboxRelayRunner` (virtual-thread loop, drains on stop) + ITs (AC-010-04, 07)
- [x] T4 — scoring: alerts go through the outbox in the same transaction as the assessments
- [x] T5 — alert-service: resolutions go through the outbox in the same transaction as the transition
- [x] T6 — `DltReplayer` + admin endpoint + IT (AC-010-03)
- [x] T7 — `CircuitBreakerMlScorer` (Resilience4j) + test (AC-010-05)
- [x] T8 — Explicit timeouts + `TimeoutConfigurationTest` per service (AC-010-06)
- [x] T9 — Docs: concept 13, 12; architecture; ADR index
