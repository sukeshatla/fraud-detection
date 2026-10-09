# Plan — Feature 004 Persistence & query performance

## 1. Approach
- The scoring listener becomes a **batch listener**: one `poll()` (up to 500 records) is one call to the use case.
- The use case scores every fresh record, then persists all assessments through the `AssessmentRepository` port **in one DB transaction** with three `JdbcTemplate.batchUpdate` calls (transaction, risk_score, rule_hit).
- The PostgreSQL driver flag `reWriteBatchedInserts=true` turns the N single-row `INSERT`s into a few multi-row `INSERT … VALUES (…),(…)` statements.
- Offsets are committed after the batch succeeds. A crash replays the batch, and `ON CONFLICT DO NOTHING` makes the replay a no-op.

```mermaid
sequenceDiagram
    participant K as Kafka
    participant L as Batch listener
    participant S as ScoreTransactionService
    participant R as Redis
    participant DB as PostgreSQL
    K->>L: poll() → up to 500 records
    L->>L: parse all; first invalid index i?
    L->>S: scoreBatch(records[0..i))
    loop each fresh record
        S->>R: activity windows (Lua)
        S->>S: rules
    end
    S->>DB: BEGIN · batch INSERT transaction · batch INSERT risk_score · batch INSERT rule_hit · COMMIT
    S->>K: alerts (REVIEW/DECLINE)
    S->>R: mark processed
    L-->>K: BatchListenerFailedException(i) if a poison pill exists → commit < i, DLT record i, redeliver the rest
```

## 2. Schema (`scoring` schema, Flyway `V1__scoring_schema.sql`)
| Table | Key | Notes |
|-------|-----|-------|
| `transaction` | `id uuid` (deterministic UUIDv7) | `UNIQUE(transaction_id)`, index `(account_id, occurred_at DESC)` |
| `risk_score` | `transaction_pk` → transaction | rule score, ML columns (nullable, filled by F006), decision |
| `rule_hit` | identity | `UNIQUE(transaction_pk, rule_code)` makes replays idempotent |

**Database per service:** each service owns a schema (`scoring`, later `alerts`) and never reads another service's tables. Cross-service data flows through events or APIs.

## 3. Deterministic UUIDv7
```
 48 bits: unix ms of occurredAt | 4 bits: version 7 | 12 bits ┐
 2 bits: variant | 62 bits ─────────────────────────────────── ┴─ SHA-256(transactionId)
```
- **Time-ordered**, so B-tree inserts land on the right-most leaf page. That means no page splits all over the index, a hot cache, and small indexes.
- **Deterministic per transactionId**, so a replay produces the same PK and the batch insert stays rewrite-friendly (`VALUES`, no `INSERT … SELECT` lookups).

## 4. Key decisions
| Decision | Alternatives | Why |
|----------|--------------|-----|
| `JdbcTemplate.batchUpdate` | JPA `saveAll` | Write-only hot path. No persistence context, no dirty checking, no IDENTITY batching trap. |
| Batch listener + `BatchListenerFailedException(index)` | Record listener | Batching needs the whole poll. The index tells Spring exactly which record to dead-letter. |
| One transaction per poll | One per record | 1 commit (fsync) per 500 records instead of 500 |
| `hikari.data-source-properties.reWriteBatchedInserts` | URL parameter | Also applies when Testcontainers supplies the URL |
| Pool size 10 | Default / "bigger is better" | Rule of thumb ≈ cores × 2. More connections than cores just adds contention. |

## 5. Test strategy
| AC | Test |
|----|------|
| 01 | Flyway runs in every IT; `ddl-auto` is never enabled |
| 02, 03 | `JdbcAssessmentRepositoryIT` (batch, replay no-op, rule hits) |
| 04 | `QueryPlanIT` (EXPLAIN JSON: Index Scan on the composite index, no Sort node) |
| 05 | `BatchInsertBenchmarkIT` (row-by-row vs batch, results logged) |
| 06 | `UuidV7Test` |
| 07 | `AccountHistoryControllerTest`, `JdbcAssessmentRepositoryIT` |
| Batch semantics | `ScoreTransactionServiceTest`, `TransactionReceivedListenerTest`, `ScoringPipelineIT` |
