# ADR-0005: Testing with Testcontainers, no in-memory fakes

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
H2 doesn't behave like PostgreSQL (partial indexes, `ON CONFLICT`, isolation semantics). Embedded Kafka doesn't reproduce real broker behaviour, and there is no faithful in-memory Redis Lua runtime. Tests against fakes give false confidence in exactly the places this project is meant to show off.

## Decision
- Integration tests (`*IT`) use **Testcontainers** with the same images as `docker compose`, wired through Spring Boot's `@ServiceConnection`.
- Unit and slice tests (`*Test`) mock ports, never infrastructure clients.
- Surefire runs `*Test` on `mvn test`. Failsafe runs `*IT` on `mvn verify`.

## Consequences
- ✅ Tests prove real behaviour.
- ❌ Integration tests require Docker and take seconds rather than milliseconds. Containers are reused per JVM to keep it fast.
