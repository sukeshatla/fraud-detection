package com.fraudplatform.lab;

import static com.fraudplatform.lab.Timing.sleep;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The classic deadlock: T1 holds A and wants B, T2 holds B and wants A. Shown with a transfer
 * between two accounts, each locked by its own lock.
 */
class DeadlockTest {

    static final class Account {
        final String id;
        final ReentrantLock lock = new ReentrantLock();
        int balance = 100;

        Account(String id) {
            this.id = id;
        }
    }

    /** Naive: lock "from" then "to". Two opposite transfers can deadlock. */
    static void transferNaive(Account from, Account to, int amount) throws InterruptedException {
        from.lock.lockInterruptibly();
        try {
            sleep(50); // widen the window
            to.lock.lockInterruptibly();
            try {
                from.balance -= amount;
                to.balance += amount;
            } finally {
                to.lock.unlock();
            }
        } finally {
            from.lock.unlock();
        }
    }

    /** Fix: always lock in a global order (by id), whatever the transfer direction. */
    static void transferOrdered(Account from, Account to, int amount) {
        Account first = from.id.compareTo(to.id) < 0 ? from : to;
        Account second = first == from ? to : from;
        first.lock.lock();
        try {
            second.lock.lock();
            try {
                from.balance -= amount;
                to.balance += amount;
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }

    @Test
    @DisplayName("AC-009-05: opposite transfers deadlock; ThreadMXBean detects it; interrupting recovers")
    void naiveTransfersDeadlockAndAreDetected() throws Exception {
        Account a = new Account("A");
        Account b = new Account("B");
        Thread t1 = Thread.ofPlatform().start(() -> runInterruptibly(() -> transferNaive(a, b, 10)));
        Thread t2 = Thread.ofPlatform().start(() -> runInterruptibly(() -> transferNaive(b, a, 10)));

        long[] deadlocked = awaitDeadlock();
        assertThat(deadlocked).contains(t1.threadId(), t2.threadId()); // what a thread dump would report

        t1.interrupt(); // lockInterruptibly() lets us break the cycle (synchronized could not)
        t1.join(1_000);
        t2.join(1_000);
        assertThat(t1.isAlive() || t2.isAlive()).isFalse();
    }

    @Test
    @DisplayName("AC-009-05: global lock ordering: 1,000 opposite transfers never deadlock and money is conserved")
    void orderedLockingNeverDeadlocks() throws Exception {
        Account a = new Account("A");
        Account b = new Account("B");
        AtomicInteger done = new AtomicInteger();
        Thread t1 = Thread.ofPlatform().start(() -> repeat(500, () -> transferOrdered(a, b, 1), done));
        Thread t2 = Thread.ofPlatform().start(() -> repeat(500, () -> transferOrdered(b, a, 1), done));

        t1.join(TimeUnit.SECONDS.toMillis(10));
        t2.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(done).hasValue(1_000);
        assertThat(a.balance + b.balance).isEqualTo(200);
    }

    private static long[] awaitDeadlock() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 100; i++) {
            long[] ids = threads.findDeadlockedThreads(); // also sees j.u.c. locks, not just monitors
            if (ids != null) {
                return ids;
            }
            sleep(20);
        }
        throw new AssertionError("expected a deadlock");
    }

    private interface Interruptible {
        void run() throws InterruptedException;
    }

    private static void runInterruptibly(Interruptible work) {
        try {
            work.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // gave up: lock released by finally blocks
        }
    }

    private static void repeat(int times, Runnable work, AtomicInteger counter) {
        for (int i = 0; i < times; i++) {
            work.run();
            counter.incrementAndGet();
        }
    }
}
