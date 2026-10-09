# ADR-0004: Java 21 + Spring Boot 4 with virtual threads

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
The ingestion path is I/O-bound (Redis, Kafka ack). Platform threads (≈1 MB stack each, a pool of ~200) cap concurrency. Reactive WebFlux solves that, but at a large cost in readability and debuggability.

## Decision
- Java 21 LTS and Spring Boot 4.1.
- `spring.threads.virtual.enabled=true`: Tomcat, `@Async`, and Kafka listener containers run on virtual threads.
- Keep the **blocking, imperative** programming model.
- Bound concurrency explicitly with `Semaphore`s where a downstream resource is finite (DB pool, ML model). Virtual threads remove the *thread* limit, not the *resource* limit.

## Consequences
- ✅ Thread-per-request code that scales like async code for I/O-bound work.
- ✅ Plain stack traces and debuggers work.
- ❌ Pinning risk on JDK 21 when blocking inside `synchronized`. We prefer `ReentrantLock` in hot paths and watch with `-Djdk.tracePinnedThreads=full`. (JDK 24+ removes most pinning, see JEP 491.)
- ❌ `ThreadLocal`-heavy libraries can bloat memory with millions of threads.

## Alternatives considered
- **WebFlux/Reactor:** comparable throughput, much higher cognitive load.
- **Bigger platform thread pools:** memory-bound, context-switch overhead.
