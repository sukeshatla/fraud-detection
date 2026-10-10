package com.fraudplatform.scoring.infrastructure.metrics;

import static com.fraudplatform.scoring.domain.TransactionBuilder.NOW;
import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerScoringMetricsTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MicrometerScoringMetrics metrics = new MicrometerScoringMetrics(meters);

    @Test
    @DisplayName("AC-012-02: decisions are counted, scores distributed, detection latency timed")
    void recordsAssessments() {
        var tx = aTransaction().occurredAt(NOW).build();
        metrics.assessed(List.of(
                new RiskAssessment(tx, 80, 85, Decision.DECLINE, List.of(), NOW.plusMillis(250), new MlPrediction(0.9, "v1")),
                new RiskAssessment(tx, 0, 0, Decision.APPROVE, List.of(), NOW.plusMillis(150))));
        metrics.batchProcessed(2, Duration.ofMillis(40));

        assertThat(meters.get("transactions_scored_total").tag("decision", "DECLINE").counter().count()).isEqualTo(1);
        assertThat(meters.get("transactions_scored_total").tag("decision", "APPROVE").counter().count()).isEqualTo(1);
        assertThat(meters.get("fraud_risk_score").summary().max()).isEqualTo(85);
        assertThat(meters.get("fraud_detection_latency").timer().count()).isEqualTo(2);
        assertThat(meters.get("fraud_detection_latency").timer().max(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(250);
        assertThat(meters.get("ml_scored_total").counter().count()).isEqualTo(1);
        assertThat(meters.get("scoring_batch_size").summary().totalAmount()).isEqualTo(2);
        assertThat(meters.get("scoring_batch_duration").timer().count()).isEqualTo(1);
    }
}
