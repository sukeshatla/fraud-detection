# Tasks — Feature 012 Observability

- [x] T1 — Dependencies (Prometheus registry, OTel bridge + OTLP exporter) and shared config (histograms, SLO buckets, sampling, Kafka observation)
- [x] T2 — `RequestIdFilter` in each service; `x-request-id` on Kafka records; MDC in consumers
- [x] T3 — Business metrics through ports: `ScoringMetrics`, `AlertMetrics`; ingestion counter; dead-letter counter
- [x] T4 — Trace bridging: transaction metadata (scoring), `CurrentPropagationHeaders` (alerts), non-observed relay template
- [x] T5 — ITs per service (`@AutoConfigureTracing`)
- [x] T6 — Prometheus (DNS SD, alert rules), Grafana (provisioned dashboard), Jaeger in compose; ECS logs
- [x] T7 — Smoke test checks scraping + traces; docs
