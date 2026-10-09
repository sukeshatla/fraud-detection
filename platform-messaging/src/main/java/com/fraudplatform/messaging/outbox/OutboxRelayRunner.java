package com.fraudplatform.messaging.outbox;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the relay in a loop on a virtual thread: immediately again after a full batch (catching
 * up), otherwise after {@code pollInterval}; after an error, after {@code errorBackoff}.
 *
 * <p><b>Graceful stop</b>: {@link #stop()} wakes the loop and waits for the in-flight batch to
 * finish. It never interrupts it, because an interrupted relay would roll back after messages
 * were already sent (harmless but wasteful duplicates).
 */
public class OutboxRelayRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayRunner.class);

    private final IntSupplier relay;
    private final int batchSize;
    private final Duration pollInterval;
    private final Duration errorBackoff;
    private volatile boolean running;
    private volatile CountDownLatch stopSignal = new CountDownLatch(1);
    private Thread worker;

    public OutboxRelayRunner(IntSupplier relay, int batchSize, Duration pollInterval, Duration errorBackoff) {
        this.relay = relay;
        this.batchSize = batchSize;
        this.pollInterval = pollInterval;
        this.errorBackoff = errorBackoff;
    }

    public OutboxRelayRunner(OutboxRelay relay, int batchSize, Duration pollInterval, Duration errorBackoff) {
        this((IntSupplier) relay::relayOnce, batchSize, pollInterval, errorBackoff);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        stopSignal = new CountDownLatch(1);
        worker = Thread.ofVirtual().name("outbox-relay").start(this::loop);
    }

    private void loop() {
        while (running) {
            Duration pause;
            try {
                pause = relay.getAsInt() >= batchSize ? Duration.ZERO : pollInterval;
            } catch (RuntimeException e) {
                log.warn("Outbox relay failed, retrying in {}: {}", errorBackoff, e.toString());
                pause = errorBackoff;
            }
            if (!pause.isZero()) {
                waitOrStop(pause);
            }
        }
    }

    private void waitOrStop(Duration pause) {
        try {
            stopSignal.await(pause.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public void stop() {
        Thread current;
        synchronized (this) {
            running = false;
            stopSignal.countDown();
            current = worker;
        }
        if (current != null) {
            try {
                current.join(Duration.ofSeconds(30)); // let the in-flight batch finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running || (worker != null && worker.isAlive());
    }
}
