# Feature 007 — Alert management API

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 003, 004 |
| Concepts | [N+1 & composite indexing](../../docs/concepts/03-n-plus-one-and-composite-indexing.md), [Two-layer concurrent-write protection](../../docs/concepts/05-two-layer-concurrent-write-protection.md) |

## 1. Problem / motivation
Analysts work a shared queue of alerts. The queue must load fast under heavy filtering, and two analysts acting on the same alert must never overwrite each other silently.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-007-01 | alert-service consumes `fraud.alerts.v1` and inserts an `alert` (status `OPEN`) idempotently (`UNIQUE(transaction_id)`). |
| AC-007-02 | `GET /api/v1/alerts?status=OPEN&severity=HIGH&page=0&size=50&sort=createdAt,desc` returns a page served by the index `(status, severity, created_at DESC)`. Keyset (cursor) pagination is available via `?after=<cursor>`. |
| AC-007-03 | **N+1:** Listing 50 alerts with their rule hits issues a **constant** number of SQL statements (≤ 3: page, hits for the page, count), asserted with Hibernate statistics. A companion test shows the naive lazy-loading mapping issuing 1 + N. |
| AC-007-04 | `PATCH /api/v1/alerts/{id}` with `{status, version}` performs the state transition `OPEN → UNDER_REVIEW → CONFIRMED_FRAUD / FALSE_POSITIVE`. Invalid transitions → `409`. |
| AC-007-05 | **Layer 1:** the update acquires the Redis lock `lock:alert:{id}` (`SET NX PX 5000`, random token). If the lock is held → `409 Conflict` immediately. Release uses a compare-and-delete Lua script. |
| AC-007-06 | **Layer 2:** the JPA `@Version` column is checked. A stale `version` → `409` with the current state in the body. This holds even if the Redis lock expired mid-request (proven by a test that bypasses layer 1). |
| AC-007-07 | **Given** 50 concurrent PATCHes on one alert (test with `ExecutorService` + `CountDownLatch` start gate), **then** exactly 1 succeeds and 49 get `409`, and the `alert_event` audit table has exactly 1 row. |
| AC-007-08 | Every transition writes an `alert_event` audit row in the same DB transaction. |
| AC-007-09 | Resolving an alert (`CONFIRMED_FRAUD` / `FALSE_POSITIVE`) publishes `AlertResolvedEvent` to `fraud.alert-resolutions.v1`. scoring-service consumes it and, for `FALSE_POSITIVE`, clears the account's high-risk flag (closes AC-005-05). |
| AC-007-10 | `GET /api/v1/alerts/{id}` returns the alert with its rule hits and audit trail. Unknown id → `404`. |

## 3. Out of scope
Assignment/round-robin routing of alerts to analysts.
