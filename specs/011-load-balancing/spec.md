# Feature 011 — Load balancing & horizontal scaling

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 002 |
| Concepts | [Load balancing](../../docs/concepts/11-load-balancing.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-011-01 | `docker compose --profile app` runs **3** ingestion, **3** scoring and **2** alert-service replicas behind NGINX (`least_conn` for ingestion, round-robin for alerts). Upstreams re-resolve Docker DNS (`resolve`), so replicas can scale without an NGINX reload. `infra/smoke-test.sh` proves it end to end. |
| AC-011-02 | NGINX passive health checks (`max_fails=3 fail_timeout=10s`) with `proxy_next_upstream`, which retries a failed request on another replica. POST is retried only because ingestion is idempotent (Idempotency-Key + dedupe). The "kill a replica under load" error-rate check runs in Feature 013. |
| AC-011-03 | SSE connections work through NGINX (`proxy_buffering off`, long `proxy_read_timeout`). |
| AC-011-04 | NGINX keeps or mints `X-Request-Id`, forwards it upstream, returns it to the client and logs it with the chosen upstream. (The services put it into their logs/MDC in Feature 012.) |
| AC-011-05 | Readiness probe goes `OUT_OF_SERVICE` during shutdown so the LB drains the instance before it stops. |
| AC-011-06 | **Consumer scaling:** 3 scoring replicas on a 12-partition topic each receive 4 partitions. Killing one triggers a rebalance (cooperative-sticky assignor), and processing continues. Rebalance events are logged. |
| AC-011-07 | The concept doc compares L4 vs L7, round-robin / least-connections / consistent hashing, client-side LB, and why rate limits must be distributed when behind an LB. |
