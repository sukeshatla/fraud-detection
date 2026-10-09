package com.fraudplatform.scoring.infrastructure.ml;

import com.fraudplatform.scoring.application.MlScorer;
import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Bulkhead</b> around model inference, built on a {@link Semaphore}.
 *
 * <p>With virtual threads there's no thread-pool size to cap concurrency. Thousands of callers can
 * reach a CPU-bound or remote model at once. The semaphore caps how many are <i>inside</i>.
 * Callers that can't get a permit within {@code acquireTimeout} don't queue: they <b>fall back</b>
 * to rules-only, and {@code ml_fallback_total} records why. Load degrades scoring quality
 * gracefully instead of building unbounded latency.
 */
public class SemaphoreBulkheadMlScorer implements MlScorer {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreBulkheadMlScorer.class);

    private final MlScorer delegate;
    private final Semaphore permits;
    private final Duration acquireTimeout;
    private final Counter bulkheadFull;
    private final Counter errors;

    public SemaphoreBulkheadMlScorer(MlScorer delegate, int maxConcurrent, Duration acquireTimeout, MeterRegistry meters) {
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrent);
        this.acquireTimeout = acquireTimeout;
        this.bulkheadFull = fallbackCounter(meters, "bulkhead_full");
        this.errors = fallbackCounter(meters, "error");
    }

    private static Counter fallbackCounter(MeterRegistry meters, String reason) {
        return Counter.builder("ml_fallback_total")
                .description("Transactions scored rules-only because the model was unavailable")
                .tag("reason", reason)
                .register(meters);
    }

    @Override
    public Optional<MlPrediction> score(Transaction transaction, AccountActivity activity) {
        try {
            if (!permits.tryAcquire(acquireTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                bulkheadFull.increment();
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            bulkheadFull.increment();
            return Optional.empty();
        }
        try {
            return delegate.score(transaction, activity);
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("Model inference failed for {}, scoring rules-only: {}", transaction.transactionId(), e.toString());
            return Optional.empty();
        } finally {
            permits.release(); // always, or permits leak and the bulkhead slowly closes for good
        }
    }
}
