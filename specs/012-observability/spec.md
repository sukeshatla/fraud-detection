# Feature 012 — Observability

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 007 |
| Concepts | [Observability](../../docs/concepts/14-observability.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-012-01 | Every service exposes `/actuator/prometheus`. Prometheus scrapes them in compose. |
| AC-012-02 | RED metrics (rate, errors, duration histograms with SLO buckets) for HTTP, plus custom metrics: `transactions_ingested_total`, `fraud_score` distribution, `alerts_raised_total{severity}`, `kafka consumer lag`, `ml_fallback_total`, cache hit ratio. |
| AC-012-03 | A provisioned Grafana dashboard JSON lives in `infra/grafana/`. |
| AC-012-04 | Distributed tracing via Micrometer Tracing + OpenTelemetry. The trace context propagates HTTP → Kafka headers → consumer, so one trace shows ingest→score→alert. |
| AC-012-05 | Structured JSON logs (Spring Boot structured logging, ECS format) with `traceId`, `spanId` and `requestId`. |
| AC-012-06 | Alert rules: consumer lag > 10k for 5 min, p99 ingest latency > 100 ms, DLT rate > 0. |
