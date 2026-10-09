package com.fraudplatform.lab;

import static com.fraudplatform.lab.Timing.sleep;
import static com.fraudplatform.lab.Timing.time;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-009-04: 10,000 tasks that each block for 100 ms (think: waiting on Redis/Kafka/DB).
 *
 * <ul>
 *   <li>Fixed pool of 200 platform threads: 10,000 / 200 = 50 waves × 100 ms ≈ 5 s.
 *   <li>Virtual thread per task: all 10,000 park at once ≈ 100 ms + overhead.
 * </ul>
 * Same code, same blocking calls. Only the threads changed.
 */
class VirtualVersusPlatformThreadsTest {

    static final int TASKS = 10_000;
    static final int BLOCK_MS = 100;

    @Test
    @DisplayName("AC-009-04: blocking I/O-bound work: virtual threads finish ~an order of magnitude faster than a 200-thread pool")
    void virtualThreadsScaleBlockingWork() throws Exception {
        Duration platform = time(() -> run(Executors.newFixedThreadPool(200)));
        Duration virtual = time(() -> run(Executors.newVirtualThreadPerTaskExecutor()));

        System.out.printf("%n10,000 × sleep(100 ms): fixed pool(200) = %d ms, virtual threads = %d ms%n",
                platform.toMillis(), virtual.toMillis());
        assertThat(platform).isGreaterThan(Duration.ofMillis((long) TASKS / 200 * BLOCK_MS)); // ≥ 50 waves
        assertThat(virtual.multipliedBy(5)).isLessThan(platform);
    }

    private static void run(ExecutorService executor) {
        try (executor) { // Java 21: close() waits for all submitted tasks
            IntStream.range(0, TASKS).forEach(i -> executor.submit(() -> sleep(BLOCK_MS)));
        }
    }
}
