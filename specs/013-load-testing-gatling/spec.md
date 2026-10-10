# Feature 013 — Load testing with Gatling

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 011 |
| Concepts | [Load testing with Gatling](../../docs/concepts/09-load-testing-gatling.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-013-01 | `load-tests` Maven module using the Gatling **Java DSL**, run with `./mvnw -pl load-tests gatling:test`. |
| AC-013-02 | **Baseline simulation:** open workload model, ramp to 1,000 req/s over 2 min, hold 5 min. Assertions: p99 < 50 ms, error rate < 0.1%. The build fails if they are violated. |
| AC-013-03 | **Spike simulation:** 0 → 5,000 req/s in 10 s. This verifies the rate limiter returns 429s and the system recovers. |
| AC-013-04 | **Fraud-pattern feeder:** a realistic mix (95% normal, 5% fraud patterns: velocity bursts, card testing, impossible travel), so the scoring and alert paths are exercised. |
| AC-013-05 | **Soak simulation:** 30 min at 50% capacity to detect leaks (heap, connections, consumer lag trend). |
| AC-013-06 | The results (HTML report screenshots + numbers) are recorded in the concept doc, along with the before/after of at least one optimisation (e.g. batch size, linger.ms). |
| AC-013-07 | A nightly CI job runs a short smoke simulation against the compose stack. |
