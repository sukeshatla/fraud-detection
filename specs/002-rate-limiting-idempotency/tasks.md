# Tasks — Feature 002 Distributed rate limiting & idempotency

- [x] T1 — `Transaction.fingerprint()` stable across equal transactions, differs on any field (AC-002-06)
- [x] T2 — Use case: claim → publish → complete; replay on COMPLETED; 422 on fingerprint mismatch; 409 on IN_PROGRESS; release on failure (AC-002-05..07)
- [x] T3 — `token_bucket.lua` + `RedisTokenBucketRateLimiter`: burst then reject (AC-002-01)
- [x] T4 — Concurrency: 100 virtual threads, capacity 10 → exactly 10 allowed (AC-002-03)
- [x] T5 — Two limiter instances share one quota (AC-002-02)
- [x] T6 — Fail-open + `rate_limiter_failures_total` counter (AC-002-04)
- [x] T7 — `RateLimitInterceptor`: 429 ProblemDetail + headers; per-client quotas from config (AC-002-01)
- [x] T8 — `RedisIdempotencyStore`: atomic `SET NX`, complete with `XX`, release (AC-002-05, 07)
- [x] T9 — HTTP IT: duplicate POST returns the original eventId and publishes once (AC-002-05)
- [x] T10 — Docs: concept 06 + 12 status, roadmap
