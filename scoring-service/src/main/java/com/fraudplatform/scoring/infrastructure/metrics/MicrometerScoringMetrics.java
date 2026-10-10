package com.fraudplatform.scoring.infrastructure.metrics;

import com.fraudplatform.scoring.application.ScoringMetrics;
import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.RiskAssessment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Business metrics for scoring (Prometheus names in parentheses):
 * <ul>
 *   <li>{@code transactions_scored_total{decision}}: decision mix; a sudden DECLINE spike is an incident.
 *   <li>{@code fraud_risk_score}: score distribution with buckets at the decision thresholds (40, 75).
 *   <li>{@code fraud_detection_latency_seconds}: occurredAt → scored, the platform's end-to-end SLO.
 *   <li>{@code ml_scored_total}: how many transactions the model contributed to (vs fallbacks).
 *   <li>{@code scoring_batch_size}, {@code scoring_batch_duration_seconds}: throughput shape per poll.
 * </ul>
 */
public class MicrometerScoringMetrics implements ScoringMetrics {

    private final Map<Decision, Counter> decisions = new EnumMap<>(Decision.class);
    private final DistributionSummary riskScore;
    private final Timer detectionLatency;
    private final Counter mlScored;
    private final DistributionSummary batchSize;
    private final Timer batchDuration;

    public MicrometerScoringMetrics(MeterRegistry meters) {
        for (Decision decision : Decision.values()) {
            decisions.put(decision, Counter.builder("transactions_scored_total").tag("decision", decision.name()).register(meters));
        }
        riskScore = DistributionSummary.builder("fraud_risk_score")
                .serviceLevelObjectives(20, 40, 60, 75, 90)
                .register(meters);
        detectionLatency = Timer.builder("fraud_detection_latency")
                .description("From transaction time to scored")
                .serviceLevelObjectives(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2))
                .register(meters);
        mlScored = Counter.builder("ml_scored_total").register(meters);
        batchSize = DistributionSummary.builder("scoring_batch_size").register(meters);
        batchDuration = Timer.builder("scoring_batch_duration").publishPercentileHistogram().register(meters);
    }

    @Override
    public void assessed(List<RiskAssessment> assessments) {
        for (RiskAssessment a : assessments) {
            decisions.get(a.decision()).increment();
            riskScore.record(a.riskScore());
            Duration latency = Duration.between(a.transaction().occurredAt(), a.scoredAt());
            if (!latency.isNegative()) {
                detectionLatency.record(latency);
            }
            if (a.mlPrediction().isPresent()) {
                mlScored.increment();
            }
        }
    }

    @Override
    public void batchProcessed(int size, Duration elapsed) {
        batchSize.record(size);
        batchDuration.record(elapsed);
    }
}
