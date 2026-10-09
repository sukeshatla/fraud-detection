package com.fraudplatform.lab;

import static com.fraudplatform.lab.Timing.sleep;
import static com.fraudplatform.lab.Timing.time;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Per-key mutual exclusion: serialise work for the same account, run different accounts in
 * parallel. {@code computeIfAbsent} is atomic, so two threads can never create two different locks
 * for one key.
 *
 * <p>(Across a cluster, Kafka's key → partition → single consumer gives the same guarantee for free.
 * That's why scoring needs no locks for per-account ordering.)
 */
class PerKeyLockTest {

    static final class KeyedLocks {
        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        void withLock(String key, Runnable work) {
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            try {
                work.run();
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    @DisplayName("AC-009-05: same key is serialised (no lost updates), different keys run in parallel")
    void perKeySerialisation() throws Exception {
        KeyedLocks locks = new KeyedLocks();
        Map<String, Integer> balances = new ConcurrentHashMap<>(Map.of("acc-1", 0, "acc-2", 0));

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                String key = i % 2 == 0 ? "acc-1" : "acc-2";
                // read-modify-write that WOULD race without the lock (get + put are two steps)
                pool.submit(() -> locks.withLock(key, () -> balances.put(key, balances.get(key) + 1)));
            }
        }
        assertThat(balances).containsEntry("acc-1", 500).containsEntry("acc-2", 500);

        Duration twoKeys = time(() -> {
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                pool.submit(() -> locks.withLock("acc-1", () -> sleep(200)));
                pool.submit(() -> locks.withLock("acc-2", () -> sleep(200)));
            }
        });
        assertThat(twoKeys).isLessThan(Duration.ofMillis(350)); // parallel, not 400 ms
    }
}
