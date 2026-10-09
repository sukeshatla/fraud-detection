package com.fraudplatform.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.ingestion.application.IdempotencyRecord;
import com.fraudplatform.ingestion.application.IngestionReceipt;
import com.fraudplatform.ingestion.support.RedisTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisIdempotencyStoreIT {

    @Container
    static final GenericContainer<?> REDIS = RedisTestSupport.redisContainer();

    private static RedisIdempotencyStore store;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        redis = RedisTestSupport.template(REDIS);
        store = new RedisIdempotencyStore(redis, JsonMapper.builder().build(), Duration.ofHours(24), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("AC-002-05: first claim wins; second sees IN_PROGRESS with the stored fingerprint")
    void firstClaimWins() {
        String key = uniqueKey();

        assertThat(store.claim(key, "fp-1")).isEmpty();
        assertThat(store.claim(key, "fp-1")).contains(IdempotencyRecord.inProgress("fp-1"));
    }

    @Test
    @DisplayName("AC-002-05: completed record round-trips the original receipt, with a 24h TTL")
    void completedRecordIsReturned() {
        String key = uniqueKey();
        IngestionReceipt receipt = new IngestionReceipt("txn-1", UUID.randomUUID(), Instant.parse("2026-10-09T18:15:30.120Z"), false);
        store.claim(key, "fp-1");

        store.complete(key, IdempotencyRecord.completed("fp-1", receipt));

        assertThat(store.claim(key, "fp-1")).contains(IdempotencyRecord.completed("fp-1", receipt));
        assertThat(redis.getExpire("idem:" + key)).isGreaterThan(Duration.ofHours(23).toSeconds());
    }

    @Test
    @DisplayName("Released key can be claimed again (client retry after a failure)")
    void releaseAllowsRetry() {
        String key = uniqueKey();
        store.claim(key, "fp-1");

        store.release(key);

        assertThat(store.claim(key, "fp-1")).isEmpty();
    }

    @Test
    @DisplayName("AC-002-07: 50 concurrent claims of one key → exactly one winner")
    void exactlyOneConcurrentWinner() throws Exception {
        String key = uniqueKey();
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<IdempotencyRecord>>> claims = IntStream.range(0, 50)
                    .mapToObj(i -> pool.submit(() -> {
                        startGate.await();
                        return store.claim(key, "fp-1");
                    }))
                    .toList();
            startGate.countDown();

            long winners = 0;
            for (Future<Optional<IdempotencyRecord>> claim : claims) {
                if (claim.get().isEmpty()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
