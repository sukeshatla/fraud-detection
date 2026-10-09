# ADR-0007: Shared `platform-messaging` library for outbox, backoff and DLT replay

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
Feature 010 needs the same infrastructure in scoring-service (alerts) and alert-service (resolutions): a transactional outbox writer and relay, a jittered backoff for Kafka error handlers, and dead-letter replay. ADR-0003 restricts `common` to event contracts.

## Decision
Create `platform-messaging`, a library of **infrastructure-only** helpers:
- `OutboxWriter`, `OutboxRelay`, `OutboxRelayRunner`
- `JitteredExponentialBackOff`
- `DltReplayer`

Rules:
- No domain types, no business logic, no Spring Boot auto-configuration. Services wire the beans explicitly in their composition roots.
- Each service owns its own `outbox` table, in its own schema, via its own Flyway migration (database per service still holds).
- It's used only from each service's `infrastructure` layer (enforced by the existing ArchUnit rules: `application`/`domain` may depend only on the JDK).

## Consequences
- ✅ One tested implementation of subtle code (ordering, locking, at-least-once).
- ❌ A shared library couples services' upgrade cadence for these helpers. It's acceptable in a monorepo, and the API surface is small.

## Alternatives considered
- **Copy per service:** two copies of concurrency-sensitive code drift apart.
- **Debezium/CDC:** removes the relay entirely but adds Kafka Connect to operate. It's the documented next step at higher volume.
