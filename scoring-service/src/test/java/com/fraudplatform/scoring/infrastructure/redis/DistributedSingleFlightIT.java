package com.fraudplatform.scoring.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.application.HighRiskAccountSource;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.RedisTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** AC-005-04 across instances: two "pods" × 50 threads, one shared Redis → the database is hit once. */
@Testcontainers
class DistributedSingleFlightIT {

    @Container
    static final GenericContainer<?> REDIS = Containers.redis();

    @Test
    @DisplayName("AC-005-04: 2 instances × 50 concurrent misses → exactly 1 DB load")
    void oneLoadAcrossInstances() throws Exception {
        String acc = "acc-" + UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        HighRiskAccountSource slowDb = new HighRiskAccountSource() {
            @Override
            public Optional<HighRiskAccount> findActiveFlag(String accountId) {
                loads.incrementAndGet();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of(new HighRiskAccount(accountId, 90, "VELOCITY", Instant.now()));
            }

            @Override
            public void recordClearance(String accountId, Instant at) {}
        };
        AccountRiskService podA = pod(slowDb);
        AccountRiskService podB = pod(slowDb);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                AccountRiskService pod = i % 2 == 0 ? podA : podB;
                results.add(pool.submit(() -> {
                    start.await();
                    return pod.riskOf(acc).isHighRisk();
                }));
            }
            start.countDown();
            for (Future<Boolean> r : results) {
                assertThat(r.get()).isTrue();
            }
        }
        assertThat(loads).hasValue(1);
    }

    private static AccountRiskService pod(HighRiskAccountSource source) {
        StringRedisTemplate redis = RedisTestSupport.template(REDIS); // each pod has its own connection
        RedisHighRiskAccountCache cache = new RedisHighRiskAccountCache(redis, new SimpleMeterRegistry(),
                Duration.ofHours(1), Duration.ofMinutes(5), 0.10, Duration.ofSeconds(2));
        return new AccountRiskService(cache, source, Clock.systemUTC(), Duration.ofMillis(1000), Duration.ofMillis(20));
    }
}
