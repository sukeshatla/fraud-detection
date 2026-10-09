package com.fraudplatform.lab;

import static com.fraudplatform.lab.Timing.sleep;
import static com.fraudplatform.lab.Timing.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coordination primitives: latches, barriers, futures, bounded queues. */
class CoordinationTest {

    @Test
    @DisplayName("CountDownLatch as a start gate: N threads released at the same instant (how our race tests work)")
    void countDownLatchStartGate() throws Exception {
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Long> startedAt = new CopyOnWriteArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    startedAt.add(System.nanoTime());
                    return null;
                });
            }
            ready.await();   // everyone is in position
            go.countDown();  // fire
        }
        long spread = startedAt.stream().mapToLong(Long::longValue).max().orElseThrow()
                - startedAt.stream().mapToLong(Long::longValue).min().orElseThrow();
        assertThat(Duration.ofNanos(spread)).isLessThan(Duration.ofMillis(100));
    }

    @Test
    @DisplayName("CyclicBarrier: workers meet after each phase; the barrier action runs once per phase")
    void cyclicBarrierPhases() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        CyclicBarrier barrier = new CyclicBarrier(3, () -> log.add("phase complete"));
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int w = 0; w < 3; w++) {
                pool.submit(() -> {
                    for (int phase = 0; phase < 2; phase++) {
                        log.add("work");
                        barrier.await(); // reusable, unlike CountDownLatch
                    }
                    return null;
                });
            }
        }
        assertThat(log).containsExactly("work", "work", "work", "phase complete", "work", "work", "work", "phase complete");
    }

    @Test
    @DisplayName("AC-009-01: CompletableFuture fan-out: wall time ≈ slowest call, not the sum; a slow call times out to a default")
    void completableFutureFanOut() throws Exception {
        ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Duration elapsed = time(() -> {
                var risk = CompletableFuture.supplyAsync(() -> slow(150, 40), vt);
                var device = CompletableFuture.supplyAsync(() -> slow(100, 10), vt);
                var bureau = CompletableFuture.supplyAsync(() -> slow(5_000, 99), vt) // a hung dependency…
                        .completeOnTimeout(0, 300, TimeUnit.MILLISECONDS);              // …contributes 0 after 300 ms
                int total = CompletableFuture.allOf(risk, device, bureau)
                        .thenApply(v -> risk.join() + device.join() + bureau.join())
                        .get();
                assertThat(total).isEqualTo(50);
            });
            assertThat(elapsed).isBetween(Duration.ofMillis(290), Duration.ofMillis(1_000)); // not 5,250 ms
        } finally {
            vt.shutdownNow(); // cancel the abandoned call: a timeout in the caller doesn't stop the callee
        }
    }

    @Test
    @DisplayName("CompletableFuture errors: orTimeout fails fast; exceptionally() turns failure into a fallback")
    void completableFutureErrors() {
        var timedOut = CompletableFuture.supplyAsync(() -> slow(200, 1)).orTimeout(50, TimeUnit.MILLISECONDS);
        assertThatThrownBy(timedOut::join).isInstanceOf(CompletionException.class).hasCauseInstanceOf(TimeoutException.class);

        var recovered = CompletableFuture.<Integer>supplyAsync(() -> {
            throw new IllegalStateException("model down");
        }).exceptionally(e -> -1);
        assertThat(recovered.join()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Bounded BlockingQueue = backpressure: a fast producer is slowed to the consumer's pace")
    void boundedQueueBackpressure() throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);
        Thread consumer = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 20; i++) {
                take(queue);
                sleep(10); // slow consumer
            }
        });
        Duration producing = time(() -> {
            for (int i = 0; i < 20; i++) {
                queue.put(i); // blocks while the queue is full
            }
        });
        consumer.join();
        assertThat(producing).isGreaterThan(Duration.ofMillis(100)); // throttled, not instant
        assertThat(queue.offer(1, 10, TimeUnit.MILLISECONDS)).isTrue(); // offer(timeout) = non-blocking alternative
    }

    private static int slow(long millis, int value) {
        sleep(millis);
        return value;
    }

    private static void take(BlockingQueue<Integer> queue) {
        try {
            queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
