# 09 · Load testing with Gatling

> **Status:** ✅ Implemented in [Feature 013](../../specs/013-load-testing-gatling/spec.md)
> **Code:** [`load-tests/`](../../load-tests/src/test/java/com/fraudplatform/load) · [`failover.sh`](../../load-tests/failover.sh) · [nightly workflow](../../.github/workflows/load-smoke.yml)

## TL;DR
Unit tests prove correctness. Load tests prove **SLOs**: latency percentiles and error rates at a target throughput. Gatling runs them as code (Java DSL) and fails the build when an assertion is violated.

## Test types

| Type | Shape | Question it answers |
|------|-------|---------------------|
| **Baseline / load** | Ramp to the expected peak, hold | Do we meet SLOs at the expected peak? |
| **Stress** | Keep increasing until it breaks | Where is the knee, and how does it fail? |
| **Spike** | 0 → 5× in seconds | Do the rate limiter and autoscaling react? Does it recover? |
| **Soak / endurance** | Moderate load for hours | Leaks: memory, connections, consumer lag drift |

## Open vs closed workload models (a favourite interview question)
- **Closed:** a fixed number of users, each waiting for the response before sending the next request. When the system slows down, the arrival rate **drops**, which hides the problem (*coordinated omission*).
- **Open:** requests arrive at a fixed rate regardless of response time. This is how real payment traffic behaves. **Use open models for public APIs.**

Every simulation here uses the open model. The rate is a `-Drate` system property:
```java
setUp(
    transactions.injectOpen(
        rampUsersPerSec(10).to(1000).during(Duration.ofMinutes(2)),
        constantUsersPerSec(1000).during(Duration.ofMinutes(5))))
    .protocols(http.baseUrl("http://localhost"))
    .assertions(
        global().responseTime().percentile(99.0).lt(50),
        global().failedRequests().percent().lt(0.1));
```

## Measured on this repo
**Setup:** a laptop (Apple M5 Pro, Docker limited to 18 vCPU / 8 GB) running **everything**: Kafka, PostgreSQL, Redis, 3×ingestion, 3×scoring, 2×alert-service, NGINX, Prometheus, Grafana, Jaeger *and* Gatling. Treat absolute numbers as a lower bound, and the comparisons as the point.

| Run | Requests | Failed | p50 | p95 | p99 | max |
|-----|---------:|-------:|----:|----:|----:|----:|
| Baseline, 200 rps (30 s ramp + 60 s hold) | 15,075 | 0 | 8 ms | 11 ms | 15 ms | 372 ms |
| Baseline, 500 rps | 37,575 | 0 | 7 ms | 9 ms | **10 ms** | 20 ms |
| Spike: one client at 2,000 rps (quota 100 rps) | 30,000 | 0 | 1 ms | 7 ms | 8 ms | 51 ms |
| …recovery right after, 50 rps, spread clients | 1,500 | 0 | 9 ms | 11 ms | 11 ms | 16 ms |

- During the 200 rps baseline, **detection latency** (transaction time → scored, which includes the batch and outbox) had p95 **0.96 s**, inside the 1 s SLO. 1,436 alerts were raised by the feeder's fraud patterns.
- **Spike:** ~27,670 requests were answered **429** and **0** got 5xx. About 2,330 were accepted, which matches the token-bucket arithmetic (200 burst + 100/s × 20 s ≈ 2,200). The runaway client is throttled in about a millisecond, other tenants aren't affected, and normal traffic flows immediately afterwards.

### Before/after: NGINX retry during a replica crash (AC-013-06)
`failover.sh`: 100 rps for 60 s, `docker kill` one of 3 ingestion replicas at t≈20 s, run twice.

| Gateway config | Failed | p99 | max |
|----------------|-------:|----:|----:|
| `proxy_next_upstream error timeout http_502 http_503 non_idempotent` (shipped) | **0 / 6,000** | 10 ms | 16 ms |
| retries off | 3 / 6,000 (0.05%) | 12 ms | **2,004 ms** |

Without retries, the requests in flight to the dead replica fail, and some clients wait out the 2 s connect timeout. With retries, NGINX re-sends them to a healthy replica, which is safe here *only* because ingestion is idempotent. The difference is small at 100 rps because passive health checks remove the dead replica within one failure. It grows with traffic and with slower failure detection.

## Reading results
- Look at **p99 / p99.9**, never the average. One slow call per page view hurts many users.
- Correlate with server metrics (Feature 012): CPU, GC pauses, Hikari pool wait, Kafka producer `record-queue-time`, consumer lag.
- Use **Little's Law**: `concurrency = throughput × latency`. 1,000 rps × 0.05 s = 50 in-flight requests. This sizes pools and checks results for plausibility.

## Interview questions
<details><summary>What is coordinated omission?</summary>

When the load generator waits for slow responses before sending more, it stops sending during exactly the periods when the system is slow, so those bad latencies are under-sampled. Open models and tools that correct for it (Gatling open injection, wrk2, HdrHistogram) avoid it.
</details>
