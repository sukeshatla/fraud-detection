package com.fraudplatform.scoring.infrastructure.redis;

import com.fraudplatform.scoring.application.ProcessedEventStore;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@code processed:{eventId}} markers. The TTL must exceed the longest realistic redelivery
 * window (topic retention / max consumer outage); 7 days by default.
 */
public class RedisProcessedEventStore implements ProcessedEventStore {

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisProcessedEventStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        return Boolean.TRUE.equals(redis.hasKey(key(eventId)));
    }

    @Override
    public void markProcessed(UUID eventId) {
        redis.opsForValue().set(key(eventId), "1", ttl);
    }

    private static String key(UUID eventId) {
        return "processed:" + eventId;
    }
}
