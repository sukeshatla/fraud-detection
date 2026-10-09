package com.fraudplatform.messaging.backoff;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Exponential backoff with random jitter, for Kafka error handlers.
 *
 * <p>{@code interval(n) = min(initial × multiplierⁿ, max) × (1 ± jitter)}, then STOP after
 * {@code maxRetries}. Spring's {@code ExponentialBackOff} has no jitter. Without it, every consumer
 * that failed on the same outage retries at the same instants, so a recovering dependency gets hit
 * by synchronised waves (thundering herd).
 */
public class JitteredExponentialBackOff implements BackOff {

    private final long initialMillis;
    private final double multiplier;
    private final double jitter;
    private final long maxMillis;
    private final int maxRetries;

    public JitteredExponentialBackOff(Duration initial, double multiplier, double jitter, Duration max, int maxRetries) {
        if (jitter < 0 || jitter >= 1) {
            throw new IllegalArgumentException("jitter must be in [0, 1)");
        }
        this.initialMillis = initial.toMillis();
        this.multiplier = multiplier;
        this.jitter = jitter;
        this.maxMillis = max.toMillis();
        this.maxRetries = maxRetries;
    }

    @Override
    public BackOffExecution start() {
        return new BackOffExecution() {
            private int attempt;

            @Override
            public long nextBackOff() {
                if (attempt >= maxRetries) {
                    return STOP;
                }
                double base = Math.min(initialMillis * Math.pow(multiplier, attempt++), maxMillis);
                double factor = jitter == 0 ? 1 : 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
                return Math.round(base * factor);
            }
        };
    }
}
