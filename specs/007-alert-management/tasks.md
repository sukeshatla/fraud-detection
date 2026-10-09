# Tasks — Feature 007 Alert management API

- [x] T1 — `AlertResolvedEvent` contract + golden test
- [x] T2 — Domain: `AlertStatus` state machine, `Severity` policy
- [x] T3 — Ingest use case + Kafka listener + JDBC idempotent insert (AC-007-01)
- [x] T4 — Flyway `alerts` schema: queue index, partial index, audit table
- [x] T5 — JPA entities with `@Version`; query adapter: offset + keyset, hits in one query (AC-007-02, 03, 10)
- [x] T6 — `ReviewAlertService`: lock → version check → transition → audit → publish (AC-007-04..09)
- [x] T7 — `RedisAlertLock` (SET NX PX + compare-and-delete)
- [x] T8 — REST controllers + ProblemDetail mapping
- [x] T9 — ITs: query/N+1, concurrency (50 PATCHes; lock bypass), ingestion pipeline
- [x] T10 — scoring-service: `AlertResolutionListener` → clear account risk
- [x] T11 — Docs: concepts 03 + 05, architecture, compose
