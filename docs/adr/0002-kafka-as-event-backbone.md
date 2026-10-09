# ADR-0002: Kafka as the event backbone

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
Transactions arrive in bursts (Black Friday, payroll days). Scoring is CPU-heavy and has variable latency (ML, cache misses). If ingestion called scoring synchronously, scoring latency and outages would leak straight to the payment gateway.

## Decision
- Use **Apache Kafka** (KRaft mode, no ZooKeeper) between ingestion → scoring → alerting.
- Partition by `accountId` to get per-account ordering.
- Producers: `acks=all`, `enable.idempotence=true`. Consumers: manual offset commit after side effects, plus idempotent handlers.
- Payloads: JSON with an explicit `schemaVersion`, and topic names suffixed with `.v1`. Contracts live in the `common` module as Java records.

## Consequences
- ✅ Temporal decoupling: ingestion is unaffected while scoring is down. The backlog is replayable.
- ✅ Horizontal scaling of scoring up to the partition count.
- ✅ New consumers (analytics, case management) attach without touching producers.
- ❌ Eventual consistency: the gateway gets `202 Accepted`, not a synchronous decision. (A synchronous "pre-auth score" API is out of scope for this repo.)
- ❌ Operational weight of running Kafka.
- ❌ Shared Java contract module couples deploys of producer and consumer for breaking changes. Mitigated by additive-only changes within a `.v1` topic.

## Alternatives considered
| Option | Why not |
|--------|---------|
| RabbitMQ | No log retention/replay, weaker per-key ordering at scale |
| Synchronous REST chain | Couples availability and latency |
| Redis Streams | Viable at small scale, weaker durability/ecosystem |
| Avro + Schema Registry | Better evolution guarantees; deferred to keep the showcase lightweight. This is the documented upgrade path. |
