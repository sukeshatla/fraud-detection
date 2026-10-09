# Feature 011 — Load balancing & horizontal scaling

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 002 |
| Concepts | [Load balancing](../../docs/concepts/11-load-balancing.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-011-01 | `docker compose` runs **3** ingestion replicas and **2** alert-service replicas behind NGINX (`least_conn` for ingestion, `round_robin` for alerts). |
| AC-011-02 | NGINX passive health checks (`max_fails=3 fail_timeout=10s`). Killing a replica during a Gatling run causes < 0.1% errors. |
| AC-011-03 | SSE connections work through NGINX (`proxy_buffering off`, long `proxy_read_timeout`). |
| AC-011-04 | NGINX adds `X-Request-Id`, and the services propagate it into logs (MDC). |
| AC-011-05 | Readiness probe goes `OUT_OF_SERVICE` during shutdown so the LB drains the instance before it stops. |
| AC-011-06 | **Consumer scaling:** 3 scoring replicas on a 12-partition topic each receive 4 partitions. Killing one triggers a rebalance (cooperative-sticky assignor), and processing continues. Rebalance events are logged. |
| AC-011-07 | The concept doc compares L4 vs L7, round-robin / least-connections / consistent hashing, client-side LB, and why rate limits must be distributed when behind an LB. |
