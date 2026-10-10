# Concept library

Each note explains a concept, shows where this codebase uses it, lists the pitfalls, and ends with interview questions and answers.

| # | Concept | Where in the code | Status |
|---|---------|-------------------|--------|
| 01 | [Event-driven decoupling with Kafka](01-event-driven-kafka.md) | ingestion → `transactions.received.v1` | ✅ producer |
| 02 | [Distributed caching with Redis](02-distributed-caching-redis.md) | risk cache, velocity windows | ✅ F003 · F005 |
| 03 | [N+1 queries and composite indexing](03-n-plus-one-and-composite-indexing.md) | alert queue, account history | ✅ F004 · F007 |
| 04 | [JDBC batch processing](04-jdbc-batch-processing.md) | scoring persistence | ✅ F004 |
| 05 | [Two-layer concurrent-write protection](05-two-layer-concurrent-write-protection.md) | alert review | ✅ F007 |
| 06 | [Distributed rate limiting](06-distributed-rate-limiting.md) | ingestion edge | ✅ F002 |
| 07 | [JDK 21 virtual threads](07-virtual-threads.md) | all services, parallel scoring | ✅ measured |
| 08 | [Concurrency primitives: threads, semaphores, locks](08-concurrency-primitives.md) | bulkheads, parallel scoring, `concurrency-lab` | ✅ F005 · F006 · F009 |
| 09 | [Load testing with Gatling](09-load-testing-gatling.md) | `load-tests/` | ✅ F013 |
| 10 | [CI/CD pipeline](10-ci-cd-pipeline.md) | `.github/workflows/`, `k8s/` | ✅ F014 |
| 11 | [Load balancing](11-load-balancing.md) | NGINX, consumer groups | ✅ F011 |
| 12 | [Idempotency & exactly-once](12-idempotency-and-exactly-once.md) | every hop | ✅ F002 · F003 · F004 · F010 |
| 13 | [Resilience patterns](13-resilience-patterns.md) | timeouts, retries, DLT replay, breaker, outbox | ✅ F010 |
| 14 | [Observability](14-observability.md) | metrics, logs, traces, alerts | ✅ F012 |
| 15 | [ML in production](15-ml-in-production.md) | feature parity, blending, bulkhead | ✅ F006 |
| 16 | [API security: OAuth2, JWT, RBAC](16-api-security-oauth2-jwt.md) | every API, dashboard login, PII in logs | ✅ F015 |

Also see: [Architecture](../architecture/README.md) · [ADRs](../adr/README.md) · [Constitution](../constitution.md)
