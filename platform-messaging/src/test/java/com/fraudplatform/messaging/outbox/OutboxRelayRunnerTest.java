package com.fraudplatform.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRelayRunnerTest {

    @Test
    @DisplayName("AC-010-07: stop() lets the in-flight batch finish, then no further batches start")
    void stopDrainsInFlightBatch() throws Exception {
        CountDownLatch batchStarted = new CountDownLatch(1);
        CountDownLatch releaseBatch = new CountDownLatch(1);
        AtomicBoolean batchCompleted = new AtomicBoolean();
        AtomicInteger batches = new AtomicInteger();
        OutboxRelayRunner runner = new OutboxRelayRunner(() -> {
            if (batches.incrementAndGet() == 1) {
                batchStarted.countDown();
                await(releaseBatch); // a slow Kafka round trip is in progress
                batchCompleted.set(true);
                return 10;
            }
            return 0;
        }, 500, Duration.ofMillis(10), Duration.ofMillis(10));

        runner.start();
        assertThat(batchStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread stopper = Thread.ofVirtual().start(runner::stop);
        Thread.sleep(100);
        assertThat(stopper.isAlive()).as("stop() waits for the in-flight batch").isTrue();

        releaseBatch.countDown();
        stopper.join(5_000);

        assertThat(batchCompleted).isTrue();
        assertThat(runner.isRunning()).isFalse();
        int afterStop = batches.get();
        Thread.sleep(100);
        assertThat(batches.get()).isEqualTo(afterStop);
    }

    @Test
    @DisplayName("A failing relay is retried after a backoff rather than killing the loop")
    void survivesFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch recovered = new CountDownLatch(1);
        OutboxRelayRunner runner = new OutboxRelayRunner(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("Kafka unavailable");
            }
            recovered.countDown();
            return 0;
        }, 500, Duration.ofMillis(10), Duration.ofMillis(10));

        runner.start();
        assertThat(recovered.await(5, TimeUnit.SECONDS)).isTrue();
        runner.stop();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
