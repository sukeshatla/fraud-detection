# 06 · Distributed rate limiting

> **Status:** 📝 Planned: [Feature 002](../../specs/002-rate-limiting-idempotency/spec.md)

## Why "distributed"
Three ingestion instances behind a load balancer, each with an in-memory limit of 100 req/s, allow **300 req/s** in total, and that number changes whenever you scale. The counter must live in one shared place: **Redis**.

## Algorithms

| Algorithm | Idea | Burst | Memory | Notes |
|-----------|------|-------|--------|-------|
| Fixed window | `INCR rl:client:{minute}` | 2× at the window boundary | O(1) | Simplest. Boundary spike problem. |
| Sliding log | Sorted set of timestamps | Exact | O(requests) | Precise, memory-heavy |
| Sliding window counter | Weighted current + previous window | ≈ exact | O(1) | Good compromise |
| **Token bucket** ✅ | Tokens refill at rate r up to capacity b; a request takes 1 | Allows bursts ≤ b | O(1) | Industry standard (AWS, Stripe). **We use this.** |
| Leaky bucket | Queue drained at a constant rate | Smooths output | O(queue) | Traffic shaping |

## Token bucket in Redis, atomically
Read-modify-write from Java (`GET` → compute → `SET`) **races** between instances. A Lua script runs atomically on the Redis server:

```lua
-- KEYS[1]=bucket  ARGV: capacity, refill_per_ms, now_ms, requested
local b = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local capacity, rate, now, req = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4])
local tokens = tonumber(b[1]) or capacity
local ts     = tonumber(b[2]) or now
tokens = math.min(capacity, tokens + (now - ts) * rate)       -- refill since last call
local allowed = tokens >= req
if allowed then tokens = tokens - req end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate))    -- idle buckets disappear
return { allowed and 1 or 0, tokens }
```
- One round trip (`EVALSHA`). No lock, no race.
- `now` comes from the caller or from `redis.call('TIME')`. Using Redis time avoids clock skew between app servers.

## HTTP contract
```
HTTP/1.1 429 Too Many Requests
Retry-After: 1
RateLimit-Limit: 100
RateLimit-Remaining: 0
RateLimit-Reset: 1
```

## Design decisions
- **Fail open** when Redis is down. For a fraud-ingestion path, dropping legitimate payments is worse than briefly allowing excess traffic. Alert on it. (A login endpoint would fail closed.)
- **Key by authenticated client** (from the JWT in Feature 015), never by a header the client can spoof.
- **Layered limits:** NGINX `limit_req` (coarse, per IP, protects the fleet) + app-level per-client quota (business contract).

## Interview questions
<details><summary>Token bucket vs leaky bucket?</summary>

Token bucket limits the average rate but allows bursts up to the bucket size, which is good for APIs. Leaky bucket enforces a constant output rate by queueing, which is good for shaping traffic to a fragile downstream.
</details>

<details><summary>Redis becomes the bottleneck. Now what?</summary>

Shard by client key (Redis Cluster). Use local token pre-fetching (each instance leases e.g. 10 tokens at a time, trading precision for fewer round trips). Or approximate with local limiters at limit/N plus periodic sync.
</details>
