# 14 · Observability

> **Status:** 📝 Planned: [Feature 012](../../specs/012-observability/spec.md) · Actuator health probes present since [Feature 001](../../specs/001-transaction-ingestion/spec.md)

## Three pillars plus one
| Signal | Tool | Answers |
|--------|------|---------|
| **Metrics** | Micrometer → Prometheus → Grafana | Is it healthy? Trends, SLOs, alerts |
| **Logs** | Structured JSON (ECS) | What happened to this request? |
| **Traces** | Micrometer Tracing → OpenTelemetry | Where did the time go across services? |
| **Profiles** | JFR, async-profiler | Why is this code slow? |

## Method cheat-sheet
- **RED** for services: Rate, Errors, Duration.
- **USE** for resources: Utilisation, Saturation, Errors (CPU, pools, queues).
- **Golden signals:** latency, traffic, errors, saturation.

## Fraud-specific metrics
`transactions_ingested_total` · `fraud_score` histogram · `alerts_raised_total{severity}` · **Kafka consumer lag** (the key signal of an event-driven system's health) · `ml_fallback_total` · cache hit ratio · `rate_limited_total{client}`.

## Trace propagation across Kafka
The `traceparent` header (W3C) is injected into Kafka record headers by the producer and extracted by the consumer, so a single trace spans HTTP → ingest → Kafka → score → Kafka → alert.

## Interview questions
<details><summary>What's the first metric you'd look at in a Kafka pipeline that "feels slow"?</summary>

Consumer lag per partition. Growing lag across all partitions means you're under-provisioned. Lag on a single partition means a hot key or a stuck consumer (poison pill, long GC).
</details>
