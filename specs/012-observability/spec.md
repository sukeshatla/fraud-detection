# Feature 012 — Observability

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 007 |
| Concepts | [Observability](../../docs/concepts/14-observability.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-012-01 | Every service exposes `/actuator/prometheus`. Prometheus scrapes them in compose. |
| AC-012-02 | RED metrics (rate, errors, duration histograms with SLO buckets) for HTTP, plus business metrics: `transactions_ingested_total`, `transactions_scored_total{decision}`, `fraud_risk_score`, `fraud_detection_latency`, `alerts_raised_total{severity}`, `alert_resolutions_total{resolution}`, `alert_time_to_resolution`, Kafka consumer lag, `ml_fallback_total{reason}`, cache hit ratio, outbox and dead-letter counters. Use cases report through ports, so no metrics library leaks into the application layer. |
| AC-012-03 | A provisioned Grafana dashboard JSON lives in `infra/grafana/`. |
| AC-012-04 | Distributed tracing via Micrometer Tracing + OpenTelemetry (OTLP → Jaeger). W3C `traceparent` propagates HTTP → Kafka → consumer. Two hops need explicit bridging: **batch** scoring (no single current span) carries each record's trace context with its transaction onto the alert, and the **outbox** captures the trace context when the row is written, with the relay publishing through a non-observed template so it doesn't overwrite it. |
| AC-012-05 | Structured JSON logs (Spring Boot structured logging, ECS format) with `traceId`, `spanId` and `requestId`. |
| AC-012-06 | Alert rules: consumer lag > 10k for 5 min, p99 ingest latency > 100 ms, DLT rate > 0. |
