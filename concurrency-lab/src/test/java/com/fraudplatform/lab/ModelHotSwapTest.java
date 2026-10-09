package com.fraudplatform.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hot-swapping a model while scoring threads read it. Readers must never see a half-updated
 * model, here represented by a version and a weight that must always match.
 *
 * <ul>
 *   <li><b>Mutable fields, no sync</b>: readers can observe a torn state (new version, old weight).
 *   <li><b>ReadWriteLock</b>: many concurrent readers, an exclusive writer. Correct.
 *   <li><b>AtomicReference to an immutable model</b>: readers grab a consistent snapshot, the writer
 *       swaps one pointer. Correct, lock-free, and usually the better choice.
 * </ul>
 */
class ModelHotSwapTest {

    record Model(int version, int weight) {} // invariant: weight == version * 10

    /** Deliberately broken: two separate writes. */
    static final class MutableModel {
        volatile int version;
        volatile int weight;

        void update(int v) {
            version = v;
            Thread.onSpinWait(); // widen the race window
            weight = v * 10;
        }
    }

    @Test
    @DisplayName("AC-009-05: unsynchronised multi-field update → readers see torn state")
    void tornReads() {
        MutableModel model = new MutableModel();
        int torn = race(v -> model.update(v), () -> {
            int version = model.version;
            int weight = model.weight;
            return weight == version * 10;
        });
        assertThat(torn).isPositive();
    }

    @Test
    @DisplayName("AC-009-05: ReadWriteLock: readers never see torn state")
    void readWriteLock() {
        ReadWriteLock lock = new ReentrantReadWriteLock();
        MutableModel model = new MutableModel();
        int torn = race(v -> {
            lock.writeLock().lock();
            try {
                model.update(v);
            } finally {
                lock.writeLock().unlock();
            }
        }, () -> {
            lock.readLock().lock();
            try {
                return model.weight == model.version * 10;
            } finally {
                lock.readLock().unlock();
            }
        });
        assertThat(torn).isZero();
    }

    @Test
    @DisplayName("AC-009-05: AtomicReference<immutable record>: consistent snapshots, no locks")
    void atomicReferenceSwap() {
        AtomicReference<Model> model = new AtomicReference<>(new Model(0, 0));
        int torn = race(v -> model.set(new Model(v, v * 10)), () -> {
            Model snapshot = model.get();
            return snapshot.weight() == snapshot.version() * 10;
        });
        assertThat(torn).isZero();
    }

    /** One writer updating continuously, 4 readers checking the invariant; returns violations seen. */
    private static int race(java.util.function.IntConsumer writer, java.util.function.BooleanSupplier consistent) {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger violations = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
            pool.submit(() -> {
                for (int v = 1; v <= 2_000_000 && running.get(); v++) {
                    writer.accept(v);
                }
                running.set(false);
            });
            for (int r = 0; r < 4; r++) {
                pool.submit(() -> {
                    while (running.get()) {
                        if (!consistent.getAsBoolean()) {
                            violations.incrementAndGet();
                        }
                    }
                });
            }
        }
        return violations.get();
    }
}
