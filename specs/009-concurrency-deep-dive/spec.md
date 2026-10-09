# Feature 009 — Concurrency deep dive

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 003, 006 |
| Concepts | [Virtual threads](../../docs/concepts/07-virtual-threads.md), [Concurrency primitives](../../docs/concepts/08-concurrency-primitives.md) |

## 1. Problem / motivation
Concurrency is the most-probed topic in senior backend interviews. This feature makes each primitive **visible in real code, with a test proving its behaviour**, not just in a slide.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-009-01 | **Parallel rule evaluation:** rules run concurrently using `Executors.newVirtualThreadPerTaskExecutor()` with a per-transaction deadline (50 ms). A rule that times out contributes 0 and is logged. A test shows wall time ≈ max(rule) rather than Σ(rule). |
| AC-009-02 | **Semaphore bulkhead:** (see 006) a test with 100 concurrent callers proves no more than N are ever inside the guarded section (tracked by an `AtomicInteger` high-water mark). |
| AC-009-03 | **Pinning:** a JMH or IT benchmark compares `synchronized` vs `ReentrantLock` around blocking I/O on virtual threads on JDK 21, with `-Djdk.tracePinnedThreads`. The results are in the concept doc. |
| AC-009-04 | **Benchmark:** 10k concurrent blocking tasks (sleep 100 ms): a fixed platform pool of 200 vs virtual threads. Elapsed time and peak memory are recorded. |
| AC-009-05 | **Thread-safety primitives catalogue:** each has a small, tested example in `scoring-service` or the `concurrency-lab` module: `AtomicLong`/`LongAdder` (metrics counters), `ConcurrentHashMap.computeIfAbsent` (per-key locks), `ReadWriteLock` (model hot-swap), `CountDownLatch`/`CyclicBarrier` (test start gates), `CompletableFuture.allOf` (fan-out), and a deliberate deadlock with its fix (lock ordering). |
| AC-009-06 | `ScopedValue` vs `ThreadLocal` note for request context with virtual threads (JDK 21 preview → final in 25). |

## 3. Out of scope
Reactive (Project Reactor) comparison, discussed in the ADR only.
