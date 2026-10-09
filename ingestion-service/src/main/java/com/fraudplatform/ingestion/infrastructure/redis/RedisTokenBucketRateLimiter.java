package com.fraudplatform.ingestion.infrastructure.redis;

import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Distributed token bucket: one Lua call ({@code EVALSHA}) per request, so the check costs a
 * single round trip and is atomic across every instance sharing this Redis.
 *
 * <p><b>Fails open:</b> if Redis is unreachable the request is allowed, a warning is logged and
 * {@code rate_limiter_failures_total} is incremented. For payment ingestion, rejecting legitimate
 * traffic costs more than briefly exceeding a quota.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final StringRedisTemplate redis;
    private final Function<String, Quota> quotas;
    private final Counter failures;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, Function<String, Quota> quotas, MeterRegistry meters) {
        this.redis = redis;
        this.quotas = quotas;
        this.failures = Counter.builder("rate_limiter_failures_total")
                .description("Rate-limit checks that failed open because Redis was unavailable")
                .register(meters);
    }

    @Override
    public RateLimitDecision tryAcquire(String clientId) {
        Quota quota = quotas.apply(clientId);
        try {
            List<?> result = redis.execute(SCRIPT, List.of("rl:" + clientId),
                    String.valueOf(quota.capacity()), String.valueOf(quota.refillPerSecond()), "1");
            boolean allowed = ((Number) result.get(0)).longValue() == 1;
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfterMs = ((Number) result.get(2)).longValue();
            return allowed
                    ? RateLimitDecision.allowed(quota.capacity(), remaining)
                    : RateLimitDecision.rejected(quota.capacity(), Duration.ofMillis(retryAfterMs));
        } catch (RuntimeException e) {
            failures.increment();
            log.warn("Rate limiter unavailable, failing open for client {}: {}", clientId, e.toString());
            return RateLimitDecision.allowed(quota.capacity(), quota.capacity());
        }
    }
}
