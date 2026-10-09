package com.fraudplatform.scoring.infrastructure.redis;

import com.fraudplatform.scoring.application.HighRiskAccountCache;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * High-risk account cache in Redis.
 *
 * <ul>
 *   <li>Key {@code risk:account:{acc}} (hash: status, riskScore, reason, flaggedAt). The braces are a
 *       hash tag, consistent with the activity keys, so one account's keys share a cluster slot.
 *   <li>Written atomically (DEL + HSET + PEXPIRE in one Lua call) with a <b>jittered TTL</b>:
 *       entries written in the same burst don't all expire in the same second (avalanche).
 *   <li>Every operation fails soft: the cache is an optimisation, never a dependency for correctness.
 * </ul>
 */
public class RedisHighRiskAccountCache implements HighRiskAccountCache {

    private static final Logger log = LoggerFactory.getLogger(RedisHighRiskAccountCache.class);
    private static final String PREFIX = "risk:account:";

    private static final RedisScript<Long> PUT = RedisScript.of("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], unpack(ARGV, 2))
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            return 1""", Long.class);

    private static final RedisScript<Long> UNLOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0""", Long.class);

    private final StringRedisTemplate redis;
    private final Duration flaggedTtl;
    private final Duration clearTtl;
    private final double jitter;
    private final Duration lockTtl;
    private final Counter hits;
    private final Counter misses;
    private final Counter failures;

    public RedisHighRiskAccountCache(StringRedisTemplate redis, MeterRegistry meters, Duration flaggedTtl,
            Duration clearTtl, double jitter, Duration lockTtl) {
        this.redis = redis;
        this.flaggedTtl = flaggedTtl;
        this.clearTtl = clearTtl;
        this.jitter = jitter;
        this.lockTtl = lockTtl;
        this.hits = cacheCounter(meters, "hit");
        this.misses = cacheCounter(meters, "miss");
        this.failures = Counter.builder("cache_failures_total").tag("cache", "high_risk_account").register(meters);
    }

    private static Counter cacheCounter(MeterRegistry meters, String result) {
        return Counter.builder("cache_requests_total")
                .description("Cache lookups by outcome")
                .tag("cache", "high_risk_account")
                .tag("result", result)
                .register(meters);
    }

    static String key(String accountId) {
        return PREFIX + "{" + accountId + "}";
    }

    @Override
    public Optional<RiskStatus> get(String accountId) {
        try {
            Map<Object, Object> hash = redis.opsForHash().entries(key(accountId));
            if (hash.isEmpty()) {
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(fromHash(accountId, hash));
        } catch (RuntimeException e) {
            fail("get", e);
            misses.increment();
            return Optional.empty();
        }
    }

    @Override
    public void put(RiskStatus status) {
        List<String> args = new ArrayList<>();
        Duration ttl;
        switch (status) {
            case RiskStatus.Flagged f -> {
                ttl = jittered(flaggedTtl);
                HighRiskAccount a = f.account();
                args.addAll(List.of(String.valueOf(ttl.toMillis()), "status", "FLAGGED", "riskScore",
                        String.valueOf(a.riskScore()), "reason", a.reason(), "flaggedAt", a.flaggedAt().toString()));
            }
            case RiskStatus.Clear c -> {
                ttl = jittered(clearTtl);
                args.addAll(List.of(String.valueOf(ttl.toMillis()), "status", "CLEAR"));
            }
        }
        try {
            redis.execute(PUT, List.of(key(status.accountId())), args.toArray());
        } catch (RuntimeException e) {
            fail("put", e);
        }
    }

    @Override
    public void evict(String accountId) {
        try {
            redis.delete(key(accountId));
        } catch (RuntimeException e) {
            // Stale "flagged" entries are bounded by the TTL; log loudly so it is noticed.
            log.error("Failed to evict risk cache entry for {}; it will expire within {}", accountId, flaggedTtl, e);
            failures.increment();
        }
    }

    @Override
    public Set<String> flaggedAmong(Collection<String> accountIds) {
        if (accountIds.isEmpty()) {
            return Set.of();
        }
        List<String> ids = List.copyOf(accountIds);
        try {
            List<Object> statuses = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (String id : ids) {
                    pipelineHget(connection, key(id));
                }
                return null; // results come back from executePipelined
            });
            Set<String> flagged = new HashSet<>();
            for (int i = 0; i < ids.size(); i++) {
                if ("FLAGGED".equals(statuses.get(i))) {
                    flagged.add(ids.get(i));
                }
            }
            return flagged;
        } catch (RuntimeException e) {
            fail("flaggedAmong", e);
            return Set.of();
        }
    }

    private static void pipelineHget(RedisConnection connection, String key) {
        connection.hashCommands().hGet(key.getBytes(StandardCharsets.UTF_8), "status".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<HighRiskAccount> listFlagged(int max) {
        List<HighRiskAccount> result = new ArrayList<>();
        // SCAN walks the keyspace incrementally; KEYS would block the single-threaded server.
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(500).build())) {
            while (cursor.hasNext() && result.size() < max) {
                String key = cursor.next();
                String accountId = key.substring(PREFIX.length() + 1, key.length() - 1);
                Map<Object, Object> hash = redis.opsForHash().entries(key);
                if (!hash.isEmpty() && fromHash(accountId, hash) instanceof RiskStatus.Flagged f) {
                    result.add(f.account());
                }
            }
        } catch (RuntimeException e) {
            fail("listFlagged", e);
        }
        return result;
    }

    @Override
    public Optional<String> tryAcquireLoadLock(String accountId) {
        String token = UUID.randomUUID().toString();
        try {
            boolean acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey(accountId), token, lockTtl));
            return acquired ? Optional.of(token) : Optional.empty();
        } catch (RuntimeException e) {
            fail("lock", e);
            return Optional.of(token); // no coordination available: load locally
        }
    }

    @Override
    public void releaseLoadLock(String accountId, String token) {
        try {
            redis.execute(UNLOCK, List.of(lockKey(accountId)), token); // compare-and-delete: never free someone else's lock
        } catch (RuntimeException e) {
            fail("unlock", e); // the lock TTL releases it anyway
        }
    }

    private static String lockKey(String accountId) {
        return "lock:risk-load:{" + accountId + "}";
    }

    private Duration jittered(Duration base) {
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
        return Duration.ofMillis(Math.round(base.toMillis() * factor));
    }

    private static RiskStatus fromHash(String accountId, Map<Object, Object> hash) {
        if (!"FLAGGED".equals(hash.get("status"))) {
            return new RiskStatus.Clear(accountId);
        }
        return new RiskStatus.Flagged(new HighRiskAccount(accountId,
                Integer.parseInt((String) hash.get("riskScore")),
                (String) hash.get("reason"),
                Instant.parse((String) hash.get("flaggedAt"))));
    }

    private void fail(String operation, RuntimeException e) {
        failures.increment();
        log.warn("Risk cache {} failed, degrading gracefully: {}", operation, e.toString());
    }
}
