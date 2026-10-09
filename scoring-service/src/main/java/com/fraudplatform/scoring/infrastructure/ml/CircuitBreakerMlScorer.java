package com.fraudplatform.scoring.infrastructure.ml;

import com.fraudplatform.scoring.application.MlScorer;
import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <b>Circuit breaker</b> around model inference (Resilience4j).
 *
 * <ul>
 *   <li>CLOSED: calls go through; failures and slow calls are recorded in a sliding window.
 *   <li>OPEN (failure or slow-call rate over threshold): the model is <i>not called at all</i>.
 *       Scoring is rules-only, which protects both our latency and the struggling dependency.
 *   <li>HALF_OPEN after a wait: a few trial calls decide between CLOSED and OPEN.
 * </ul>
 * Never throws: every non-prediction becomes a rules-only fallback with a reason on
 * {@code ml_fallback_total}.
 */
public class CircuitBreakerMlScorer implements MlScorer {

    private final MlScorer delegate;
    private final CircuitBreaker breaker;
    private final Counter circuitOpen;
    private final Counter errors;

    public CircuitBreakerMlScorer(MlScorer delegate, CircuitBreaker breaker, MeterRegistry meters) {
        this.delegate = delegate;
        this.breaker = breaker;
        this.circuitOpen = Counter.builder("ml_fallback_total").tag("reason", "circuit_open").register(meters);
        this.errors = Counter.builder("ml_fallback_total").tag("reason", "error").register(meters);
    }

    @Override
    public Optional<MlPrediction> score(Transaction transaction, AccountActivity activity) {
        if (!breaker.tryAcquirePermission()) {
            circuitOpen.increment();
            return Optional.empty();
        }
        long start = System.nanoTime();
        try {
            Optional<MlPrediction> prediction = delegate.score(transaction, activity);
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return prediction;
        } catch (RuntimeException e) {
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            errors.increment();
            return Optional.empty();
        }
    }

    public CircuitBreaker.State state() {
        return breaker.getState();
    }
}
