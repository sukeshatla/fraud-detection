# Tasks — Feature 003 Rule-based scoring engine

- [x] T1 — `test-support` module: shared Kafka/Redis test helpers (refactor ingestion to use it)
- [x] T2 — `FraudAlertEvent` contract + golden-file contract test (AC-003-10)
- [x] T3 — Domain: `Transaction`, `AccountActivity`, `RuleHit`, `Decision`, `RiskAssessment`
- [x] T4 — Rules, one test class each (AC-003-02..06)
- [x] T5 — `RuleEngine`: sum, cap at 100, decision bands (AC-003-01, 07)
- [x] T6 — `ScoreTransactionService`: dedupe, record activity, evaluate, alert, mark processed (AC-003-08, 10)
- [x] T7 — `account_activity.lua` + `RedisAccountActivityStore` IT (windows, idempotency, out-of-order)
- [x] T8 — `TransactionReceivedListener` + error handler → DLT (AC-003-09)
- [x] T9 — `KafkaAlertPublisher`
- [x] T10 — `ScoringPipelineIT`: velocity burst → alert; duplicate → one alert; poison pill → DLT
- [x] T11 — ArchUnit rules for scoring-service
- [x] T12 — Docs, roadmap
