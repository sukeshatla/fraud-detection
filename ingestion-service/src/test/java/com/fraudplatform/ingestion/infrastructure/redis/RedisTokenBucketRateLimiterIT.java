package com.fraudplatform.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.support.RedisTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Token bucket against a real Redis: the Lua script is the unit under test. */
@Testcontainers
class RedisTokenBucketRateLimiterIT {

    @Container
    static final GenericContainer<?> REDIS = RedisTestSupport.redisContainer();

    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        redis = RedisTestSupport.template(REDIS);
    }

    @Test
    @DisplayName("AC-002-01: burst up to capacity succeeds, the next request is rejected with Retry-After")
    void burstThenReject() {
        RedisTokenBucketRateLimiter limiter = limiter(new Quota(200, 0.01)); // effectively no refill during the test
        String client = uniqueClient();

        long allowed = IntStream.range(0, 201).filter(i -> limiter.tryAcquire(client).allowed()).count();
        RateLimitDecision next = limiter.tryAcquire(client);

        assertThat(allowed).isEqualTo(200);
        assertThat(next.allowed()).isFalse();
        assertThat(next.remaining()).isZero();
        assertThat(next.retryAfter()).isPositive();
    }

    @Test
    @DisplayName("AC-002-03: 100 concurrent requests for 10 tokens → exactly 10 allowed (atomic Lua, no race)")
    void atomicUnderConcurrency() throws Exception {
        RedisTokenBucketRateLimiter limiter = limiter(new Quota(10, 0.01));
        String client = uniqueClient();
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = IntStream.range(0, 100)
                    .mapToObj(i -> pool.submit(() -> {
                        startGate.await();
                        return limiter.tryAcquire(client).allowed();
                    }))
                    .toList();
            startGate.countDown(); // release all threads at once to maximise contention

            long allowed = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("AC-002-02: two service instances share one quota through Redis")
    void quotaIsSharedAcrossInstances() {
        Quota quota = new Quota(50, 0.01);
        RedisTokenBucketRateLimiter instanceA = limiter(quota);
        RedisTokenBucketRateLimiter instanceB = limiter(quota);
        String client = uniqueClient();

        long allowed = IntStream.range(0, 100)
                .filter(i -> (i % 2 == 0 ? instanceA : instanceB).tryAcquire(client).allowed())
                .count();

        assertThat(allowed).isEqualTo(50);
    }

    @Test
    @DisplayName("Tokens refill over time at the configured rate")
    void refillsOverTime() throws InterruptedException {
        RedisTokenBucketRateLimiter limiter = limiter(new Quota(1, 20)); // 1 token every 50 ms
        String client = uniqueClient();

        assertThat(limiter.tryAcquire(client).allowed()).isTrue();
        assertThat(limiter.tryAcquire(client).allowed()).isFalse();
        Thread.sleep(Duration.ofMillis(120));
        assertThat(limiter.tryAcquire(client).allowed()).isTrue();
    }

    @Test
    @DisplayName("Idle buckets expire so Redis memory stays bounded")
    void bucketKeyHasTtl() {
        String client = uniqueClient();
        limiter(new Quota(10, 5)).tryAcquire(client);

        assertThat(redis.getExpire("rl:" + client)).isPositive();
    }

    private static RedisTokenBucketRateLimiter limiter(Quota quota) {
        return new RedisTokenBucketRateLimiter(redis, client -> quota, new SimpleMeterRegistry());
    }

    private static String uniqueClient() {
        return "client-" + UUID.randomUUID();
    }
}
