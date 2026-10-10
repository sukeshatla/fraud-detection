# Plan — Feature 013 Load testing with Gatling

## 1. Approach
- **`load-tests` Maven module**, Gatling **Java DSL**. Simulations live in `src/test/java`, so every build compiles them (they can't rot) but only runs them on demand (`gatling:test`).
- **Open workload model everywhere** (arrival rate, not "N users"): payment traffic doesn't slow down because we do.
- **`TransactionFeeder`**: a realistic mix: ~95% ordinary purchases over 50k accounts (lognormal amounts, everyday MCCs) and ~5% fraud *patterns* emitted back-to-back on one account (velocity bursts, card-testing probes, impossible travel). Scoring, ML, the outbox and alerting all do real work.
- **Target**: the full replicated stack behind NGINX (`infra/smoke-test.sh`), i.e. the same path production traffic takes.

## 2. Simulations
| Simulation | Shape | Assertions (fail the run) |
|------------|-------|---------------------------|
| `BaselineSimulation` | ramp → hold at `-Drate` | p99 < `-Dp99Ms`, errors < 0.1% |
| `SpikeSimulation` | one gateway jumps to 2,000 rps (quota: 100) for 20 s, then normal traffic | spike: only 202/429, no 5xx/timeouts. Recovery: errors < 0.1%, p99 within SLO. |
| `SoakSimulation` | 100 rps for 30 min | errors < 0.1% (watch Grafana for upward trends) |
| `FailoverSimulation` + `failover.sh` | steady load; an ingestion replica is `docker kill`ed mid-run, with NGINX retry ON vs OFF | error rate reported for both |
| `SmokeSimulation` | 20 rps × 30 s | ≥ 99% success, p95 < 500 ms. Runs **nightly in CI** against the full stack. |

## 3. Key decisions
| Decision | Why |
|----------|-----|
| Spike from one client id | A real spike is a runaway tenant. Per-client quotas should shed it with 429 while others are unaffected. |
| 429 counted as *success* in the spike | Shedding load is the correct answer. Only 5xx and timeouts are failures. |
| Before/after = NGINX retry ON vs OFF during a replica crash | A real, isolated optimisation whose effect is directly measurable |
| Numbers recorded with the machine they ran on | Absolute numbers on a laptop running ~15 containers are not capacity claims. Relative comparisons are what matter. |
