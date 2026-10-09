# Feature 002 — Distributed rate limiting & idempotency

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 001 |
| Concepts | [Distributed rate limiting](../../docs/concepts/06-distributed-rate-limiting.md), [Idempotency](../../docs/concepts/12-idempotency-and-exactly-once.md) |

## 1. Problem / motivation
Ingestion runs as N stateless instances behind a load balancer. An in-memory rate limiter would allow N× the intended quota, so the limit has to be **shared** in Redis. Separately, clients retry on timeouts. Without idempotency keys, one purchase can become two events and generate duplicate alerts.

## 2. User stories
- **US-1** As the platform owner, I want each API client limited to its contracted throughput, so that one noisy client can't starve the others.
- **US-2** As a payment gateway, I want to safely retry a POST, so that network timeouts never cause duplicate transactions.

## 3. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-002-01 | **Given** client `X-Client-Id: gw-1` with quota 100 req/s and burst 200, **when** it sends 201 requests instantly, **then** 200 succeed and 1 gets `429` with `Retry-After` and `RateLimit-*` headers. |
| AC-002-02 | **Given** two ingestion instances sharing Redis, **when** a client spreads requests over both, **then** the combined accepted count never exceeds the quota. |
| AC-002-03 | **Given** concurrent requests for the same client, **when** the bucket has 1 token left, **then** exactly one succeeds (atomic Lua, no read-modify-write race). |
| AC-002-04 | **Given** Redis is unavailable, **when** a request arrives, **then** the limiter **fails open** (request allowed), logs a warning, and increments `rate_limiter_failures_total`. |
| AC-002-05 | **Given** a POST with `Idempotency-Key: K` that was accepted, **when** the same key is sent again within 24h, **then** the response is `202` with the **original** `eventId`, and nothing is republished. |
| AC-002-06 | **Given** the same `Idempotency-Key` with a **different** body, **when** it is POSTed, **then** the response is `422 Unprocessable Entity` (key reuse). |
| AC-002-07 | **Given** two concurrent requests with the same key, **when** both arrive, **then** only one publishes and the other gets `409` (in-flight) or the original result. |

## 4. Non-functional requirements
- The rate-limit decision costs one Redis round-trip (`EVALSHA`). Added p99 is under 2 ms.
- Quotas are configurable per client tier without a redeploy (config property, later a DB table).

## 5. Out of scope
Global (all-clients) adaptive throttling. Gateway-level (NGINX) limits are covered in Feature 011.
