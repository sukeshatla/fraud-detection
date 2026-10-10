# Tasks — Feature 011 Load balancing & horizontal scaling

- [x] T1 — `infra/docker/service.Dockerfile` (layered jar, non-root, container-aware heap)
- [x] T2 — Gateway image: dashboard build + NGINX config (least_conn, resolve, passive checks, SSE, request ids, edge rate limit)
- [x] T3 — Compose `app` profile: 3×ingestion, 3×scoring, 2×alerts, readiness healthchecks
- [x] T4 — `infra/smoke-test.sh` end-to-end (AC-011-01)
- [x] T5 — `HealthProbesIT` (AC-011-05)
- [x] T6 — `ConsumerGroupRebalanceIT` + `LoggingRebalanceListener` (AC-011-06)
- [x] T7 — Docs: concept 11, README quick start, architecture deployment
