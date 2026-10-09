# Feature 009 — Concurrency deep dive

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 003, 006 |
| Concepts | [Virtual threads](../../docs/concepts/07-virtual-threads.md), [Concurrency primitives](../../docs/concepts/08-concurrency-primitives.md) |

## 1. Problem / motivation
Concurrency is the most-probed topic in senior backend interviews. This feature makes each primitive **visible in real code, with a test proving its behaviour**, not just in a slide.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-009-01 | **Parallel scoring across accounts:** within one Kafka poll, different accounts are scored concurrently on virtual threads, while each account's transactions stay sequential and in order. A test shows wall time ≈ (txns per account × latency) rather than (all txns × latency). Fan-out with deadlines (wall time ≈ slowest call, a hung call times out to a default) is shown with `CompletableFuture` in the lab. Parallelising the *rules themselves* was rejected: they are pure, microsecond functions, so threads would cost more than they save. |
| AC-009-02 | **Semaphore bulkhead:** (see 006) a test with 100 concurrent callers proves no more than N are ever inside the guarded section (tracked by an `AtomicInteger` high-water mark). |
| AC-009-03 | **Pinning:** a test compares `synchronized` vs `ReentrantLock` around blocking calls on virtual threads on JDK 21 (2 carrier threads). JFR `jdk.VirtualThreadPinned` events prove the cause, and the results are in the concept doc. |
| AC-009-04 | **Benchmark:** 10k concurrent blocking tasks (sleep 100 ms): a fixed platform pool of 200 vs virtual threads. Elapsed time and peak memory are recorded. |
| AC-009-05 | **Thread-safety primitives catalogue:** each has a small, tested example in `scoring-service` or the `concurrency-lab` module: `AtomicLong`/`LongAdder` (metrics counters), `ConcurrentHashMap.computeIfAbsent` (per-key locks), `ReadWriteLock` (model hot-swap), `CountDownLatch`/`CyclicBarrier` (test start gates), `CompletableFuture.allOf` (fan-out), and a deliberate deadlock with its fix (lock ordering). |
| AC-009-06 | `ScopedValue` vs `ThreadLocal` note for request context with virtual threads (JDK 21 preview → final in 25). |

## 3. Out of scope
Reactive (Project Reactor) comparison, discussed in the ADR only.
