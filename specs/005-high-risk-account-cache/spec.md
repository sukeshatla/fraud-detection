# Feature 005 — High-risk account cache

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 003 |
| Concepts | [Distributed caching with Redis](../../docs/concepts/02-distributed-caching-redis.md) |

## 1. Problem / motivation
Once an account is flagged, every subsequent transaction on it should be treated with suspicion immediately. A DB lookup per transaction is too slow, and an in-process cache isn't shared across scoring instances.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-005-01 | **Given** a transaction scores ≥ 75, **when** scoring completes, **then** `risk:account:{id}` is written (hash: level, reason, flaggedAt) with TTL 1h ± 10% random jitter. |
| AC-005-02 | **Given** a cached high-risk account, **when** its next transaction is scored, **then** `KnownHighRiskAccountRule` adds weight 50 without a DB query. |
| AC-005-03 | **Given** a cache miss, **when** the account's risk is requested (`GET /api/v1/accounts/{id}/risk`, used by alert-service and the dashboard), **then** it is loaded from PostgreSQL and populated (cache-aside). A "not high-risk" answer is cached too, with a shorter TTL (negative caching, against cache penetration). |
| AC-005-04 | **Given** 100 concurrent misses for the same key, **when** loading, **then** the DB is hit **once** (single-flight via a per-key lock or `SET NX` "loading" marker), which prevents a cache stampede. |
| AC-005-05 | **Given** an account is cleared (`DELETE /api/v1/accounts/{id}/risk`; Feature 007 triggers it when an analyst marks an alert `FALSE_POSITIVE`), **when** processed, **then** the source of truth records the clearance **first**, and only then is the cache entry evicted, so a reload can't re-flag it. |
| AC-005-08 | **Given** a batch where an earlier record is declined, **when** a later record in the **same poll** belongs to that account, **then** it already counts as high-risk. |
| AC-005-06 | Cache hit/miss ratios are exported as Micrometer metrics. |
| AC-005-07 | `GET /api/v1/accounts/high-risk` lists cached high-risk accounts using `SCAN` (never `KEYS`). |

## 3. Out of scope
Near cache (Caffeine L1 in front of Redis L2), documented as an extension.
