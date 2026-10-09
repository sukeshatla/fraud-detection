# Feature 005 — High-risk account cache

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 003 |
| Concepts | [Distributed caching with Redis](../../docs/concepts/02-distributed-caching-redis.md) |

## 1. Problem / motivation
Once an account is flagged, every subsequent transaction on it should be treated with suspicion immediately. A DB lookup per transaction is too slow, and an in-process cache isn't shared across scoring instances.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-005-01 | **Given** a transaction scores ≥ 75, **when** scoring completes, **then** `risk:account:{id}` is written (hash: level, reason, flaggedAt) with TTL 1h ± 10% random jitter. |
| AC-005-02 | **Given** a cached high-risk account, **when** its next transaction is scored, **then** `KnownHighRiskAccountRule` adds weight 50 without a DB query. |
| AC-005-03 | **Given** a cache miss, **when** the account's risk is requested by alert-service, **then** it is loaded from PostgreSQL and populated (cache-aside). |
| AC-005-04 | **Given** 100 concurrent misses for the same key, **when** loading, **then** the DB is hit **once** (single-flight via a per-key lock or `SET NX` "loading" marker), which prevents a cache stampede. |
| AC-005-05 | **Given** an analyst marks an alert `FALSE_POSITIVE`, **when** saved, **then** the account's cache entry is **evicted** (write-through invalidation). |
| AC-005-06 | Cache hit/miss ratios are exported as Micrometer metrics. |
| AC-005-07 | `GET /api/v1/accounts/high-risk` lists cached high-risk accounts using `SCAN` (never `KEYS`). |

## 3. Out of scope
Near cache (Caffeine L1 in front of Redis L2), documented as an extension.
