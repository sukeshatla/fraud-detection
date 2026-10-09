package com.fraudplatform.ingestion.infrastructure.redis;

import com.fraudplatform.ingestion.application.IdempotencyRecord;
import com.fraudplatform.ingestion.application.IdempotencyStore;
import com.fraudplatform.ingestion.application.IngestionReceipt;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotency keys in Redis.
 *
 * <ul>
 *   <li>{@code claim}: {@code SET idem:{key} <IN_PROGRESS> NX EX ttl}. Exactly one concurrent caller wins.
 *   <li>{@code complete}: {@code SET … XX EX ttl}. Only overwrites a claim that still exists.
 *   <li>{@code release}: {@code DEL}, so a failed request can be retried.
 * </ul>
 *
 * <p><b>Fails open</b> like the rate limiter: if Redis is down, requests proceed without
 * deduplication, and downstream consumers dedupe on {@code eventId} / {@code transactionId}.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);

    /** Storage format, deliberately separate from the application record so it can evolve independently. */
    record Stored(String status, String fingerprint, String transactionId, UUID eventId, Instant receivedAt) {}

    private final StringRedisTemplate redis;
    private final JsonMapper mapper;
    private final Duration ttl;
    private final Counter failures;

    public RedisIdempotencyStore(StringRedisTemplate redis, JsonMapper mapper, Duration ttl, MeterRegistry meters) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
        this.failures = Counter.builder("idempotency_store_failures_total")
                .description("Idempotency operations that failed open because Redis was unavailable")
                .register(meters);
    }

    @Override
    public Optional<IdempotencyRecord> claim(String key, String fingerprint) {
        try {
            String claim = mapper.writeValueAsString(toStored(IdempotencyRecord.inProgress(fingerprint)));
            for (int attempt = 0; attempt < 2; attempt++) {
                if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey(key), claim, ttl))) {
                    return Optional.empty();
                }
                String existing = redis.opsForValue().get(redisKey(key));
                if (existing != null) {
                    return Optional.of(toRecord(mapper.readValue(existing, Stored.class)));
                }
                // expired between SET NX and GET: try once more
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            failOpen("claim", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void complete(String key, IdempotencyRecord record) {
        try {
            redis.opsForValue().setIfPresent(redisKey(key), mapper.writeValueAsString(toStored(record)), ttl);
        } catch (RuntimeException e) {
            failOpen("complete", key, e);
        }
    }

    @Override
    public void release(String key) {
        try {
            redis.delete(redisKey(key));
        } catch (RuntimeException e) {
            failOpen("release", key, e);
        }
    }

    private void failOpen(String operation, String key, RuntimeException e) {
        failures.increment();
        log.warn("Idempotency store {} failed for key {}, continuing without dedupe: {}", operation, key, e.toString());
    }

    private static String redisKey(String key) {
        return "idem:" + key;
    }

    private static Stored toStored(IdempotencyRecord record) {
        IngestionReceipt r = record.receipt();
        return new Stored(record.status().name(), record.fingerprint(),
                r == null ? null : r.transactionId(),
                r == null ? null : r.eventId(),
                r == null ? null : r.receivedAt());
    }

    private static IdempotencyRecord toRecord(Stored s) {
        IdempotencyRecord.Status status = IdempotencyRecord.Status.valueOf(s.status());
        return status == IdempotencyRecord.Status.COMPLETED
                ? IdempotencyRecord.completed(s.fingerprint(), new IngestionReceipt(s.transactionId(), s.eventId(), s.receivedAt(), false))
                : IdempotencyRecord.inProgress(s.fingerprint());
    }
}
