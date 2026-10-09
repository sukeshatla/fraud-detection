package com.fraudplatform.messaging.backoff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

class JitteredExponentialBackOffTest {

    private final JitteredExponentialBackOff backOff =
            new JitteredExponentialBackOff(Duration.ofMillis(200), 2.0, 0.5, Duration.ofSeconds(5), 3);

    @Test
    @DisplayName("AC-010-01: intervals grow exponentially within ±jitter, then STOP after maxRetries")
    void exponentialWithJitterThenStop() {
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isBetween(100L, 300L);   // 200 ± 50%
        assertThat(execution.nextBackOff()).isBetween(200L, 600L);   // 400 ± 50%
        assertThat(execution.nextBackOff()).isBetween(400L, 1200L);  // 800 ± 50%
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    @DisplayName("Interval is capped at maxInterval (before jitter)")
    void capped() {
        BackOffExecution execution = new JitteredExponentialBackOff(Duration.ofSeconds(4), 10.0, 0.0, Duration.ofSeconds(5), 3).start();

        execution.nextBackOff();
        assertThat(execution.nextBackOff()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("Jitter actually spreads retries: consumers that failed together don't retry together")
    void spreadsRetries() {
        Set<Long> firstIntervals = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            firstIntervals.add(backOff.start().nextBackOff());
        }
        assertThat(firstIntervals).hasSizeGreaterThan(20);
    }
}
