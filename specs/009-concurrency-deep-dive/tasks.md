# Tasks — Feature 009 Concurrency deep dive

- [x] T1 — `ParallelScoringTest`: parallel ≫ sequential, per-account order, result order, bounded fan-out (AC-009-01, 02)
- [x] T2 — `ScoreTransactionService`: group by account → virtual threads + Semaphore; `max-concurrent-accounts`
- [x] T3 — `concurrency-lab` module (2 carrier threads in surefire to make pinning visible)
- [x] T4 — Virtual vs platform benchmark (AC-009-04)
- [x] T5 — Pinning + JFR (AC-009-03)
- [x] T6 — Atomics, per-key locks, hot-swap, coordination, deadlock (AC-009-05)
- [x] T7 — Concept docs 07 + 08 with measured results; ScopedValue note (AC-009-06)
