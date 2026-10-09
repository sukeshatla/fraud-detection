# ADR-0003: Service decomposition

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
We need to show distributed-systems concepts (load balancing, consumer groups, distributed locks, distributed rate limits). A single process cannot show them honestly. On the other hand, a dozen microservices would bury the ideas in boilerplate.

## Decision
Three backend services, split by **scaling dimension and failure domain**:

| Service | Scaling unit | Failure impact |
|---------|-------------|----------------|
| ingestion-service | HTTP instances behind LB | Gateway can't submit → must be most available |
| scoring-service | Kafka partitions | Detection delayed, no data lost |
| alert-service | HTTP instances behind LB | Analysts can't review, detection continues |

Plus a shared `common` library containing **only** event contracts (no business logic).

## Consequences
- ✅ Each service is small enough to read in one sitting.
- ✅ Realistic cross-service concerns (contracts, idempotency, tracing).
- ❌ More moving parts locally. Mitigated with a single `docker compose up`.

## Alternatives considered
- **Modular monolith:** a good real-world default for a small team, but it can't show distributed coordination.
- **One service per rule / model:** a nano-service anti-pattern.
