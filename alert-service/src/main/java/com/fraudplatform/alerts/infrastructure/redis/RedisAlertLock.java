package com.fraudplatform.alerts.infrastructure.redis;

import com.fraudplatform.alerts.application.AlertLock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Layer 1 of concurrent-write protection: {@code SET lock:alert:{id} <token> NX PX <ttl>}.
 *
 * <ul>
 *   <li>NX: only one holder. PX: a lease, so a crashed holder can't block the alert forever.
 *   <li>Random token + compare-and-delete release: a holder whose lease already expired can
 *       never delete the <i>next</i> holder's lock.
 *   <li>Fails open: if Redis is down, the request proceeds and layer 2 (optimistic locking)
 *       still guarantees correctness. The lock is an optimisation, not the safety net.
 * </ul>
 */
public class RedisAlertLock implements AlertLock {

    private static final Logger log = LoggerFactory.getLogger(RedisAlertLock.class);

    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0""", Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisAlertLock(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public Optional<Handle> tryLock(UUID alertId) {
        String key = "lock:alert:{" + alertId + "}";
        String token = UUID.randomUUID().toString();
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl))) {
                return Optional.empty();
            }
        } catch (RuntimeException e) {
            log.warn("Alert lock unavailable, relying on optimistic locking alone: {}", e.toString());
            return Optional.of(() -> {});
        }
        return Optional.of(() -> release(key, token));
    }

    private void release(String key, String token) {
        try {
            redis.execute(RELEASE, List.of(key), token);
        } catch (RuntimeException e) {
            log.warn("Failed to release {}; it expires within {}: {}", key, ttl, e.toString());
        }
    }
}
