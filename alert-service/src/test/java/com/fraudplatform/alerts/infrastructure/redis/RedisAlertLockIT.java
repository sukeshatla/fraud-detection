package com.fraudplatform.alerts.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.application.AlertLock;
import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.RedisTestSupport;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisAlertLockIT {

    @Container
    static final GenericContainer<?> REDIS = Containers.redis();

    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        redis = RedisTestSupport.template(REDIS);
    }

    @Test
    @DisplayName("AC-007-05: exclusive, leased (PX), released on close")
    void exclusiveAndReleased() {
        RedisAlertLock lock = new RedisAlertLock(redis, Duration.ofSeconds(5));
        UUID id = UUID.randomUUID();

        Optional<AlertLock.Handle> first = lock.tryLock(id);
        assertThat(first).isPresent();
        assertThat(lock.tryLock(id)).isEmpty();
        assertThat(redis.getExpire("lock:alert:{" + id + "}")).isBetween(1L, 5L);

        first.get().close();
        assertThat(lock.tryLock(id)).isPresent();
    }

    @Test
    @DisplayName("AC-007-05: a holder whose lease expired cannot release the NEXT holder's lock (compare-and-delete)")
    void expiredHolderCannotReleaseNewOwner() throws InterruptedException {
        RedisAlertLock lock = new RedisAlertLock(redis, Duration.ofMillis(200));
        UUID id = UUID.randomUUID();

        AlertLock.Handle slow = lock.tryLock(id).orElseThrow();
        Thread.sleep(400);                                      // lease expires (think: GC pause)
        AlertLock.Handle next = lock.tryLock(id).orElseThrow(); // someone else acquires it
        slow.close();                                           // late release must NOT free 'next'

        assertThat(lock.tryLock(id)).isEmpty();
        next.close();
    }
}
