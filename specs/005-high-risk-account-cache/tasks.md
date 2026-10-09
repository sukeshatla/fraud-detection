# Tasks — Feature 005 High-risk account cache

- [x] T1 — Domain: `HighRiskAccount`, `RiskStatus` (sealed: Flagged | Clear), `KnownHighRiskAccountRule` (AC-005-02)
- [x] T2 — Ports `HighRiskAccountCache`, `HighRiskAccountSource`
- [x] T3 — Scoring: pipelined lookup per batch, in-batch propagation, cache write after persist (AC-005-01, 08)
- [x] T4 — `AccountRiskService`: cache-aside, negative caching, two-level single-flight, clear-then-evict (AC-005-03..05)
- [x] T5 — `RedisHighRiskAccountCache`: hash + jittered TTL, pipelined EXISTS, SCAN, loader lock, hit/miss metrics (AC-005-06, 07)
- [x] T6 — `V2__account_risk_override.sql` + JDBC source query
- [x] T7 — REST: `GET /accounts/{id}/risk`, `DELETE /accounts/{id}/risk`, `GET /accounts/high-risk`
- [x] T8 — ITs: cache adapter, distributed single-flight, pipeline flagging, risk API
- [x] T9 — Docs: concept 02
