package com.fraudplatform.lab;

import static com.fraudplatform.lab.Timing.sleep;
import static com.fraudplatform.lab.Timing.time;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * AC-009-03: on JDK 21–23, a virtual thread that blocks while holding a {@code synchronized}
 * monitor is <b>pinned</b>: it can't unmount, so it blocks its carrier thread too.
 * {@code ReentrantLock} doesn't pin. (JDK 24, JEP 491, removes most monitor pinning.)
 *
 * <p>The module runs with 2 carrier threads ({@code jdk.virtualThreadScheduler.parallelism=2}).
 * 20 tasks, each holding its <i>own</i> lock (so there's no lock contention at all) while sleeping
 * 100 ms:
 * <ul>
 *   <li>synchronized: only 2 can sleep at a time → 10 waves ≈ 1,000 ms
 *   <li>ReentrantLock: all 20 park concurrently ≈ 100 ms
 * </ul>
 * JFR's {@code jdk.VirtualThreadPinned} event confirms the cause.
 */
@EnabledForJreRange(max = JRE.JAVA_23)
class PinningTest {

    static final int TASKS = 20;

    @Test
    @DisplayName("AC-009-03: blocking inside synchronized pins carriers; ReentrantLock does not")
    void synchronizedPinsReentrantLockDoesNot() throws Exception {
        AtomicInteger pinnedEvents = new AtomicInteger();
        Duration withSynchronized;
        Duration withReentrantLock;
        try (RecordingStream jfr = new RecordingStream()) {
            jfr.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ofMillis(20));
            jfr.onEvent("jdk.VirtualThreadPinned", e -> pinnedEvents.incrementAndGet());
            jfr.startAsync();

            withSynchronized = time(() -> runTasks(PinningTest::blockInsideSynchronized));
            int pinnedBySynchronized = awaitEvents(pinnedEvents, 1);
            withReentrantLock = time(() -> runTasks(PinningTest::blockInsideReentrantLock));

            System.out.printf("%n%d tasks, 2 carriers: synchronized = %d ms (pinned events: %d), ReentrantLock = %d ms%n",
                    TASKS, withSynchronized.toMillis(), pinnedBySynchronized, withReentrantLock.toMillis());
            assertThat(pinnedBySynchronized).isPositive();
        }
        assertThat(withSynchronized).isGreaterThan(withReentrantLock.multipliedBy(3));
    }

    private static void blockInsideSynchronized() {
        Object monitor = new Object();
        synchronized (monitor) {
            sleep(100); // pinned: the carrier can't run anything else
        }
    }

    private static void blockInsideReentrantLock() {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            sleep(100); // unmounts: the carrier is free for other virtual threads
        } finally {
            lock.unlock();
        }
    }

    private static void runTasks(Runnable task) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASKS; i++) {
                executor.submit(task);
            }
        }
    }

    private static int awaitEvents(AtomicInteger counter, int atLeast) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (counter.get() < atLeast && System.nanoTime() < deadline) {
            sleep(50); // JFR delivers events asynchronously
        }
        return counter.get();
    }
}
