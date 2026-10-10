# 14 · Observability

> **Status:** ✅ Implemented in [Feature 012](../../specs/012-observability/spec.md)
> **Config:** [`prometheus.yml`](../../infra/prometheus/prometheus.yml), [`alerts.yml`](../../infra/prometheus/alerts.yml), [Grafana dashboard](../../infra/grafana/dashboards/fraud-platform.json) · **Run:** `infra/smoke-test.sh`, then Grafana :3000, Prometheus :9090, Jaeger :16686

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

## Business metrics live behind ports
Use cases call `ScoringMetrics.assessed(...)` and `AlertMetrics.resolved(...)`. The Micrometer adapters live in `infrastructure`, so the application layer stays framework-free (ArchUnit-enforced) and metrics are unit-testable with a `SimpleMeterRegistry`.

## Fraud-specific metrics
`transactions_ingested_total` · `fraud_score` histogram · `alerts_raised_total{severity}` · **Kafka consumer lag** (the key signal of an event-driven system's health) · `ml_fallback_total` · cache hit ratio · `rate_limited_total{client}`.

## Trace propagation across Kafka, and the two places it breaks
The W3C `traceparent` header is injected into Kafka record headers by an *observed* `KafkaTemplate` and extracted by an *observed* listener. That covers simple hops automatically. Two hops in this system needed explicit design:

| Hop | Why automatic propagation fails | What we do |
|-----|---------------------------------|------------|
| **Batch listener** (scoring) | 500 records, no single "current span" | Each record's `traceparent` and `x-request-id` ride along with its `Transaction` as opaque metadata and are copied onto the alert it raises |
| **Transactional outbox** | The relay sends later, from another thread, under its own (unrelated) context. An observed template would stamp *that*. | Capture the trace context when the outbox row is written, then publish with a **non-observed** template |

`ScoringObservabilityIT` proves the alert carries the **same trace id** as the transaction that caused it, through batch scoring and the outbox.

## Correlation ids
NGINX keeps or mints `X-Request-Id`. Each service's `RequestIdFilter` validates it (against log injection), puts it in the MDC and echoes it. It then travels as the `x-request-id` Kafka header, and consumers restore it into their MDC. One id finds every log line of a transaction across all services, even when tracing is sampled out.

## Lessons from implementing it
- Spring Boot **disables tracing in tests**. Use `@AutoConfigureTracing`, otherwise propagation tests silently test nothing.
- Picking tracing modules by hand: without `micrometer-tracing-bridge-otel` there is a no-op `Tracer` and **no `Propagator`**, so no headers and no error.
- `${OTLP_ENDPOINT:}` (empty default) counts as "configured", and startup fails. Leave the property unset and supply it from the environment.

## Interview questions
<details><summary>What's the first metric you'd look at in a Kafka pipeline that "feels slow"?</summary>

Consumer lag per partition. Growing lag across all partitions means you're under-provisioned. Lag on a single partition means a hot key or a stuck consumer (poison pill, long GC).
</details>
