package com.fraudplatform.scoring.infrastructure.ml;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.application.MlScorer;
import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircuitBreakerMlScorerTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean healthy = new AtomicBoolean(false);

    /** A model server that fails while unhealthy and counts how often it is actually called. */
    private final MlScorer flakyModel = (tx, activity) -> {
        calls.incrementAndGet();
        if (!healthy.get()) {
            throw new IllegalStateException("model server 503");
        }
        return Optional.of(new MlPrediction(0.7, "remote-v1"));
    };

    private final CircuitBreakerMlScorer scorer = new CircuitBreakerMlScorer(flakyModel, CircuitBreaker.of("ml",
            CircuitBreakerConfig.custom()
                    .slidingWindowSize(20)
                    .minimumNumberOfCalls(20)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofMillis(200))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build()), meters);

    @Test
    @DisplayName("AC-010-05: ≥50% failures over 20 calls → OPEN → the model is not called at all; rules-only fallback")
    void opensAndShortCircuits() {
        for (int i = 0; i < 20; i++) {
            assertThat(score()).isEmpty(); // failures, each falls back
        }
        assertThat(scorer.state()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsWhenOpened = calls.get();
        for (int i = 0; i < 100; i++) {
            assertThat(score()).isEmpty();
        }
        assertThat(calls.get()).as("OPEN breaker protects the struggling dependency").isEqualTo(callsWhenOpened);
        assertThat(fallbacks("circuit_open")).isEqualTo(100.0);
        assertThat(fallbacks("error")).isEqualTo(20.0);
    }

    @Test
    @DisplayName("AC-010-05: after the wait, HALF_OPEN trial calls succeed → CLOSED, predictions flow again")
    void recoversThroughHalfOpen() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            score();
        }
        healthy.set(true);
        Thread.sleep(250);

        assertThat(score()).isPresent();
        assertThat(score()).isPresent();
        assertThat(scorer.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private Optional<MlPrediction> score() {
        return scorer.score(aTransaction().build(), AccountActivity.none());
    }

    private double fallbacks(String reason) {
        return meters.get("ml_fallback_total").tag("reason", reason).counter().count();
    }
}
