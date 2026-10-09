# 09 · Load testing with Gatling

> **Status:** 📝 Planned: [Feature 013](../../specs/013-load-testing-gatling/spec.md)

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

## Reading results
- Look at **p99 / p99.9**, never the average. One slow call per page view hurts many users.
- Correlate with server metrics (Feature 012): CPU, GC pauses, Hikari pool wait, Kafka producer `record-queue-time`, consumer lag.
- Use **Little's Law**: `concurrency = throughput × latency`. 1,000 rps × 0.05 s = 50 in-flight requests. This sizes pools and checks results for plausibility.

## Interview questions
<details><summary>What is coordinated omission?</summary>

When the load generator waits for slow responses before sending more, it stops sending during exactly the periods when the system is slow, so those bad latencies are under-sampled. Open models and tools that correct for it (Gatling open injection, wrk2, HdrHistogram) avoid it.
</details>
