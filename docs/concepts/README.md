# Concept library

Each note explains a concept, shows where this codebase uses it, lists the pitfalls, and ends with interview questions and answers.

| # | Concept | Where in the code | Status |
|---|---------|-------------------|--------|
| 01 | [Event-driven decoupling with Kafka](01-event-driven-kafka.md) | ingestion → `transactions.received.v1` | ✅ producer |
| 02 | [Distributed caching with Redis](02-distributed-caching-redis.md) | risk cache, velocity windows | 🟡 windows ✅, cache F005 |
| 03 | [N+1 queries and composite indexing](03-n-plus-one-and-composite-indexing.md) | alert queue, account history | 📝 F004/F007 |
| 04 | [JDBC batch processing](04-jdbc-batch-processing.md) | scoring persistence | 📝 F004 |
| 05 | [Two-layer concurrent-write protection](05-two-layer-concurrent-write-protection.md) | alert review | 📝 F007 |
| 06 | [Distributed rate limiting](06-distributed-rate-limiting.md) | ingestion edge | ✅ F002 |
| 07 | [JDK 21 virtual threads](07-virtual-threads.md) | all services | ✅ enabled |
| 08 | [Concurrency primitives: threads, semaphores, locks](08-concurrency-primitives.md) | ML bulkhead, parallel rules | 📝 F006/F009 |
| 09 | [Load testing with Gatling](09-load-testing-gatling.md) | `load-tests/` | 📝 F013 |
| 10 | [CI/CD pipeline](10-ci-cd-pipeline.md) | `.github/workflows/` | ✅ CI basic |
| 11 | [Load balancing](11-load-balancing.md) | NGINX, consumer groups | 📝 F011 |
| 12 | [Idempotency & exactly-once](12-idempotency-and-exactly-once.md) | every hop | 🟡 API, producer, consumer |
| 13 | [Resilience patterns](13-resilience-patterns.md) | timeouts, DLT, breaker, outbox | 🟡 timeouts |
| 14 | [Observability](14-observability.md) | metrics, logs, traces | 🟡 health probes |

Also see: [Architecture](../architecture/README.md) · [ADRs](../adr/README.md) · [Constitution](../constitution.md)
