package com.fraudplatform.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Counting under contention. {@code count++} is three steps (read, add, write), so concurrent
 * increments overwrite each other. {@code volatile} gives visibility, not atomicity.
 * {@link AtomicLong} is a CAS loop. {@link LongAdder} stripes the count across cells to avoid
 * CAS contention: best for hot write-mostly counters (metrics), with {@code sum()} paying on read.
 */
class AtomicsAndLongAdderTest {

    static final int THREADS = 8;
    static final int INCREMENTS = 200_000;
    static final long EXPECTED = (long) THREADS * INCREMENTS;

    private volatile long racyCounter;

    @Test
    @DisplayName("AC-009-05: volatile count++ loses updates under contention (race condition)")
    void volatileIncrementLosesUpdates() throws InterruptedException {
        // Lost updates are probabilistic: retry a few times; on any multi-core machine they show up immediately.
        boolean lost = false;
        for (int attempt = 0; attempt < 10 && !lost; attempt++) {
            racyCounter = 0;
            hammer(() -> racyCounter++);
            lost = racyCounter < EXPECTED;
        }
        assertThat(lost).as("expected at least one run to lose increments").isTrue();
    }

    @Test
    @DisplayName("AC-009-05: AtomicLong and LongAdder are exact")
    void atomicsAreExact() throws InterruptedException {
        AtomicLong atomic = new AtomicLong();
        LongAdder adder = new LongAdder();

        hammer(atomic::incrementAndGet);
        hammer(adder::increment);

        assertThat(atomic.get()).isEqualTo(EXPECTED);
        assertThat(adder.sum()).isEqualTo(EXPECTED);
    }

    private static void hammer(Runnable increment) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            threads[t] = Thread.ofPlatform().start(() -> {
                awaitQuietly(start);
                for (int i = 0; i < INCREMENTS; i++) {
                    increment.run();
                }
            });
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
