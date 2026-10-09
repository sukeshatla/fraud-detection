# Plan — Feature 009 Concurrency deep dive

## 1. Approach
Two deliverables:

1. **A production improvement in scoring.** The per-record cost of scoring is dominated by the Redis round trip (activity windows). A 500-record poll processed sequentially pays 500 round trips. Grouping by account and running accounts in parallel on virtual threads pays roughly *max(records per account)* round trips, without breaking per-account ordering.
2. **`concurrency-lab`**: a module of executable notes. Each primitive gets a small example and a test that **proves** or **measures** its behaviour. No Spring, plain JDK 21.

```mermaid
flowchart LR
    P[poll: 500 records] --> G[group by accountId<br/>LinkedHashMap keeps arrival order]
    G --> VT1[virtual thread: acc-1 tx1 → tx2 → tx3]
    G --> VT2[virtual thread: acc-2 tx1 → tx2]
    G --> VTn[… ≤ 64 concurrently (Semaphore)]
    VT1 & VT2 & VTn --> M[re-assemble in original order] --> DB[(one batch write)]
```

## 2. Why per-account, not per-rule
| Option | Verdict |
|--------|---------|
| Parallel rules per transaction | ❌ Rules are pure and take ~µs. Thread hand-off costs more than the work. |
| Parallel transactions, ignoring account | ❌ Breaks velocity windows (order matters per account) and races the in-batch flag propagation |
| **Parallel accounts, sequential within an account** ✅ | Same contract as Kafka partitions. The I/O latency overlaps across accounts. |

## 3. Lab catalogue
| Test | Primitive | Proves / measures |
|------|-----------|-------------------|
| `VirtualVersusPlatformThreadsTest` | virtual threads vs fixed pool | 10k blocking tasks: ~5 s vs ~0.15 s |
| `PinningTest` | `synchronized` vs `ReentrantLock` | pinning on JDK 21, detected with JFR `jdk.VirtualThreadPinned` |
| `AtomicsAndLongAdderTest` | `volatile`, `AtomicLong`, `LongAdder` | `volatile count++` loses updates; atomics don't |
| `PerKeyLockTest` | `ConcurrentHashMap.computeIfAbsent` + `ReentrantLock` | same key serialised, different keys parallel |
| `ModelHotSwapTest` | `ReadWriteLock`, `AtomicReference` | torn reads without sync; none with either |
| `CoordinationTest` | `CountDownLatch`, `CyclicBarrier`, `CompletableFuture`, `ArrayBlockingQueue` | start gates, phases, fan-out with timeout, backpressure |
| `DeadlockTest` | lock ordering, `ThreadMXBean` | deadlock detected and broken via `lockInterruptibly`; ordered locking never deadlocks |
| (scoring) `SemaphoreBulkheadMlScorerTest`, `ParallelScoringTest` | `Semaphore` | concurrency caps hold under 100 callers |

`ScopedValue` vs `ThreadLocal` (AC-009-06) is covered in concept 07. It's a preview in JDK 21 (needs `--enable-preview`), so it's documented rather than shipped.
