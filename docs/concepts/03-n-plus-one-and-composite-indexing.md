# 03 · N+1 queries and composite indexing

> **Status:** ✅ Composite index + EXPLAIN test in [Feature 004](../../specs/004-persistence-jdbc-batch/spec.md) ([`QueryPlanIT`](../../scoring-service/src/test/java/com/fraudplatform/scoring/infrastructure/persistence/QueryPlanIT.java)) · 📝 N+1 test in [Feature 007](../../specs/007-alert-management/spec.md)

## N+1 queries

### The problem
```java
List<Alert> alerts = alertRepository.findByStatus(OPEN);       // 1 query
for (Alert a : alerts) {
    a.getTransaction().getAmount();                            // +1 query per alert (lazy load)
    a.getRuleHits().size();                                    // +1 more per alert
}
// 50 alerts → 1 + 50 + 50 = 101 round-trips
```
Each round trip is cheap alone (≈0.5 ms), but 101 of them are 50 ms. Under load they also hold a pooled connection for the whole loop.

### Fixes

| Fix | How | When |
|-----|-----|------|
| **Fetch join** | `select a from Alert a join fetch a.transaction where …` | To-one associations |
| **`@EntityGraph`** | `@EntityGraph(attributePaths = {"transaction"})` on the repository method | Same, declaratively |
| **Batch fetching** | `hibernate.default_batch_fetch_size=50` turns N lazy loads into `WHERE id IN (…)` | To-many associations, where fetch-joining a collection with paging is unsafe |
| **DTO projection** | `select new AlertRow(a.id, t.amount, …)` | Read-only lists, the cheapest option |

⚠️ Fetch-joining a **collection** together with `Pageable` makes Hibernate paginate **in memory** (warning HHH90003004). Page the root entities first, then batch-fetch their children.

### Proving it in tests (Feature 007)
A `datasource-proxy` statement counter wraps the DataSource. The test asserts that listing 50 alerts issues ≤ 3 statements, so an N+1 regression fails CI.

## Composite indexing

### How a B-tree composite index is ordered
Index on `(status, severity, created_at DESC)` is sorted like a phone book: by status, then severity within status, then newest first.

```
(CLOSED, HIGH, 10:05) (CLOSED, LOW, …) … (OPEN, HIGH, 10:09) (OPEN, HIGH, 10:07) (OPEN, HIGH, 10:01) (OPEN, LOW, …)
                                          └──────── WHERE status='OPEN' AND severity='HIGH' ORDER BY created_at DESC ────────┘
                                                    one contiguous range, already sorted → no Sort node, LIMIT stops early
```

### Rules of thumb
1. **Equality columns first, then range/sort columns.** `(account_id, occurred_at)` serves `WHERE account_id = ? AND occurred_at > ?`. The reverse order does not.
2. **Leftmost prefix:** `(a, b, c)` serves queries on `a`, `a,b` and `a,b,c`, but not on `b` alone.
3. Match the **sort direction** to avoid a sort step.
4. **Covering index** (`INCLUDE (amount)`) answers the query from the index alone (Index Only Scan).
5. **Partial index** `WHERE status = 'OPEN'` keeps the hot index tiny.
6. Every index slows writes and costs memory. Index for actual query patterns, verified with `EXPLAIN (ANALYZE, BUFFERS)`.

### A lesson from `QueryPlanIT`: the planner is cost-based
The first version of the test seeded 400 accounts × 25 rows and asked for `LIMIT 50`. PostgreSQL chose **Bitmap Index Scan → Hash Join → Sort**, not the ordered index scan. That was the *right* choice: sorting 25 rows in memory is cheaper than walking the index in order. Also, `risk_score` had never been `ANALYZE`d, so its row estimate was a guess.

The ordered plan (**Limit → Nested Loop → Index Scan on `ix_transaction_account_occurred` → Index Scan on `risk_score_pkey`**, no Sort) wins in the scenario the index exists for: a **busy account** (3,000 rows) and a small `LIMIT 20`. Postgres reads 20 index entries in order and stops.

Takeaways for interviews:
- Indexes are *options*; the planner picks based on **statistics**. Keep them fresh (autovacuum/`ANALYZE`), especially after bulk loads.
- Test query plans with **realistic data distributions**, not toy data.
- Read `EXPLAIN (ANALYZE, BUFFERS)`, and look for `Sort`, `Seq Scan` on big tables, and row-estimate vs actual mismatches.

### Our indexes
| Query | Index |
|-------|-------|
| Account history / velocity | `(account_id, occurred_at DESC)` |
| Analyst queue | `(status, severity, created_at DESC)` |
| Hot open queue | `(created_at DESC) WHERE status = 'OPEN'` |
| Idempotent insert | `UNIQUE (transaction_id)` |

## Interview questions
<details><summary>How do you detect N+1 in production?</summary>

Look at statement-count-per-request metrics, Hibernate statistics (`hibernate.generate_statistics`), APM traces showing many identical queries in a loop, and `pg_stat_statements` with a high call count for a by-id query. In tests, assert statement counts.
</details>

<details><summary>Index on (a, b): does WHERE b = ? use it?</summary>

Generally no, because b isn't the leftmost column. Postgres may do a full index scan if it's cheaper than the heap, and some engines (MySQL 8, Oracle) have skip-scan. Don't rely on it: design indexes for the access path.
</details>

<details><summary>Why can an index make things slower?</summary>

Every insert and update maintains every index (write amplification). Indexes use buffer cache and memory. And for low-selectivity predicates the planner may ignore the index anyway, because a sequential scan is cheaper than random I/O.
</details>
