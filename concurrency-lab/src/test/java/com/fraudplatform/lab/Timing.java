package com.fraudplatform.lab;

import java.time.Duration;
import java.util.concurrent.Callable;

/** Tiny stopwatch for the measured examples. */
final class Timing {

    private Timing() {}

    static Duration time(ThrowingRunnable work) throws Exception {
        long start = System.nanoTime();
        work.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }

    static <T> T unchecked(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
