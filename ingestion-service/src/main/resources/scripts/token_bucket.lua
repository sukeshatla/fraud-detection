-- Token bucket, executed atomically inside Redis (no read-modify-write race between instances).
--
-- KEYS[1]  bucket key, e.g. rl:{clientId}            (hash: tokens, ts)
-- ARGV[1]  capacity          max tokens = max burst
-- ARGV[2]  refill_per_sec    sustained rate
-- ARGV[3]  requested         tokens this request costs (1)
--
-- Returns { allowed (1|0), remaining whole tokens, retry_after_ms }
--
-- Uses Redis' own clock (TIME) so app servers with skewed clocks still agree.

local key            = KEYS[1]
local capacity       = tonumber(ARGV[1])
local refill_per_sec = tonumber(ARGV[2])
local requested      = tonumber(ARGV[3])

local t      = redis.call('TIME')
local now_us = tonumber(t[1]) * 1000000 + tonumber(t[2])

local state  = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts     = tonumber(state[2])
if tokens == nil then
  tokens = capacity          -- new client starts with a full bucket
  ts = now_us
end

-- Refill for the time elapsed since the last request, capped at capacity
local elapsed_us = math.max(0, now_us - ts)
tokens = math.min(capacity, tokens + (elapsed_us * refill_per_sec / 1000000))

local allowed = 0
local retry_after_ms = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry_after_ms = math.ceil((requested - tokens) * 1000 / refill_per_sec)
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now_us))
-- An idle bucket refills completely after capacity/rate seconds; after that it carries no
-- information, so let it expire (bounded memory for one-off clients).
redis.call('PEXPIRE', key, math.ceil(capacity * 1000 / refill_per_sec) + 1000)

return { allowed, math.floor(tokens), retry_after_ms }
