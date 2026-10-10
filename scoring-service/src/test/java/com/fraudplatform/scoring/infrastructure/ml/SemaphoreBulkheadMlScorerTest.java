package com.fraudplatform.scoring.infrastructure.ml;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.application.MlScorer;
import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemaphoreBulkheadMlScorerTest {

    /** Slow delegate that records how many callers are inside it at once. */
    private static final class ConcurrencyProbe implements MlScorer {
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger highWaterMark = new AtomicInteger();

        @Override
        public Optional<MlPrediction> score(com.fraudplatform.scoring.domain.Transaction tx, AccountActivity activity) {
            int now = inside.incrementAndGet();
            highWaterMark.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inside.decrementAndGet();
            }
            return Optional.of(new MlPrediction(0.5, "probe"));
        }
    }

    @Test
    @DisplayName("AC-006-05: 100 concurrent callers, 4 permits → never more than 4 inside; the rest fall back")
    void boundsConcurrencyAndFallsBack() throws Exception {
        ConcurrencyProbe probe = new ConcurrencyProbe();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        SemaphoreBulkheadMlScorer bulkhead = new SemaphoreBulkheadMlScorer(probe, 4, Duration.ofMillis(20), meters);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Optional<MlPrediction>>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return bulkhead.score(aTransaction().build(), AccountActivity.none());
                }));
            }
            start.countDown();
        }

        long scored = 0;
        for (Future<Optional<MlPrediction>> r : results) {
            if (r.get().isPresent()) {
                scored++;
            }
        }
        double fallbacks = meters.get("ml_fallback_total").tag("reason", "bulkhead_full").counter().count();

        assertThat(probe.highWaterMark.get()).isLessThanOrEqualTo(4);
        assertThat(scored).isGreaterThanOrEqualTo(4);
        assertThat(fallbacks).isEqualTo((double) (100 - scored)).isPositive();
    }

    @Test
    @DisplayName("AC-006-05: model failure → fallback (empty), counted, permit released")
    void modelErrorFallsBack() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        MlScorer broken = (tx, activity) -> {
            throw new IllegalStateException("model exploded");
        };
        SemaphoreBulkheadMlScorer bulkhead = new SemaphoreBulkheadMlScorer(broken, 1, Duration.ofMillis(20), meters);

        assertThat(bulkhead.score(aTransaction().build(), AccountActivity.none())).isEmpty();
        assertThat(bulkhead.score(aTransaction().build(), AccountActivity.none())).isEmpty(); // permit was released
        assertThat(meters.get("ml_fallback_total").tag("reason", "error").counter().count()).isEqualTo(2.0);
    }
}
