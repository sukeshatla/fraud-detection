# Plan — Feature 005 High-risk account cache

## 1. Approach
The **source of truth** is PostgreSQL: an account is high-risk if it has a `DECLINE` in the last 24 h that is newer than its latest clearance. **Redis** caches that answer as `risk:account:{acc}` (hash), shared by all scoring instances and the risk API.

```mermaid
flowchart TB
    subgraph scoring hot path (per Kafka poll)
        B[batch of N txns] --> P[pipelined EXISTS for distinct accounts<br/>1 round trip]
        P --> R[rules incl. KNOWN_HIGH_RISK +50]
        R --> DB[(batch persist)]
        DB --> W[SET risk:account:{acc} for new DECLINEs<br/>TTL 1h ± 10%]
    end
    subgraph risk API (cache-aside)
        Q[GET /accounts/{id}/risk] --> C{Redis hit?}
        C -- yes --> H[return · metric hit]
        C -- no --> SF{in-process<br/>single-flight}
        SF -- follower --> WAIT[await leader's future]
        SF -- leader --> L{Redis loader lock<br/>SET NX PX}
        L -- won --> LOAD[(load from Postgres)] --> PUT[SET value<br/>FLAGGED 1h±10% / CLEAR 5m]
        L -- lost --> POLL[poll cache briefly, then fall back to DB]
    end
```

## 2. Cache stampede protection (two levels)
| Level | Mechanism | Collapses |
|-------|-----------|-----------|
| In-process | `ConcurrentHashMap<acc, CompletableFuture>`: the first caller loads, the others join its future | N threads in one JVM → 1 |
| Distributed | `SET lock:risk-load:{acc} NX PX 2000`: the winner loads, losers poll the cache for up to 300 ms | M instances → 1 |

## 3. Key decisions
| Decision | Alternatives | Why |
|----------|--------------|-----|
| Cache-aside + explicit invalidation | Write-through only | The cache stays disposable. Correctness never depends on Redis. |
| Clear in DB **then** evict | Evict only | Evicting alone lets the next miss reload the old DECLINE and re-flag the account |
| TTL jitter ±10% | Fixed TTL | Prevents an avalanche when keys written together expire together |
| Negative caching (CLEAR, 5 min) | Cache only positives | A flood of lookups for clean accounts would otherwise always hit the DB (penetration) |
| Pipelined batch lookup in scoring | One GET per transaction | 500 lookups → 1 round trip |
| `SCAN` for the listing | `KEYS` | `KEYS` blocks single-threaded Redis |

## 4. Test strategy
| AC | Test |
|----|------|
| 01, 08 | `ScoreTransactionServiceTest`, `ScoringPipelineIT.declinedAccountIsFlaggedForNextTransaction` |
| 02 | `KnownHighRiskAccountRuleTest`, `ScoreTransactionServiceTest` |
| 03 | `AccountRiskServiceTest`, `RiskApiIT` |
| 04 | `AccountRiskServiceTest.singleFlightInProcess`, `DistributedSingleFlightIT` (2 instances × 50 threads → 1 load) |
| 05 | `AccountRiskServiceTest.clearUpdatesSourceThenEvicts`, `RiskApiIT` |
| 06 | `RedisHighRiskAccountCacheIT.countsHitsAndMisses` |
| 07 | `RedisHighRiskAccountCacheIT.scanListsFlagged`, `AccountRiskControllerTest` |
