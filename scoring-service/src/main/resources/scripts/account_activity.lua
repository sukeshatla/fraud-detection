-- Records one transaction in an account's sliding windows and returns the account's activity.
-- One atomic round trip; idempotent per eventId (ZSET members are unique).
--
-- KEYS[1] velocity:{acc}  ZSET  score = occurredAt ms, member = eventId   (all txns, 24h retention)
-- KEYS[2] small:{acc}     ZSET  same, only txns under the card-testing threshold
-- KEYS[3] last:{acc}      HASH  country, ts, eventId, prevCountry, prevTs (latest txn + the one before)
--   The {acc} hash tag keeps all three keys in one Redis Cluster slot, which multi-key scripts require.
--
-- ARGV[1] eventId  ARGV[2] occurredAt ms  ARGV[3] isSmall (1|0)  ARGV[4] country  ARGV[5] retention ms
--
-- Returns { count60s, count1h, count24h, smallCount5m, previousCountry|'', previousTs|'' }
-- Windows use EVENT time (occurredAt), so replaying a backlog yields the same counts as live traffic.

local member    = ARGV[1]
local t         = tonumber(ARGV[2])
local is_small  = ARGV[3] == '1'
local country   = ARGV[4]
local retention = tonumber(ARGV[5])

-- Previous transaction, read BEFORE updating.
local last = redis.call('HMGET', KEYS[3], 'country', 'ts', 'eventId', 'prevCountry', 'prevTs')
local prev_country, prev_ts = last[1], last[2]
if last[3] == member then
  -- Redelivery of the event we already recorded as "last": report what came before it.
  prev_country, prev_ts = last[4], last[5]
end

redis.call('ZADD', KEYS[1], t, member)
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', t - retention)
if is_small then
  redis.call('ZADD', KEYS[2], t, member)
end
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', t - retention)

local c60s  = redis.call('ZCOUNT', KEYS[1], t - 60000, t)
local c1h   = redis.call('ZCOUNT', KEYS[1], t - 3600000, t)
local c24h  = redis.call('ZCOUNT', KEYS[1], t - 86400000, t)
local s5m   = redis.call('ZCOUNT', KEYS[2], t - 300000, t)

-- Only a NEWER transaction becomes "last" (out-of-order events must not rewind it).
local last_ts = tonumber(last[2])
if last[3] ~= member and (last_ts == nil or t >= last_ts) then
  redis.call('HSET', KEYS[3], 'country', country, 'ts', t, 'eventId', member,
             'prevCountry', last[1] or '', 'prevTs', last[2] or '')
end

for i = 1, 3 do
  redis.call('PEXPIRE', KEYS[i], retention)
end

return { c60s, c1h, c24h, s5m, prev_country or '', prev_ts or '' }
