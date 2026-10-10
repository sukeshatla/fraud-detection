package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.RiskAssessment;
import java.time.Duration;
import java.util.List;

/** Outbound port: business metrics, so the use case stays free of any metrics library. */
public interface ScoringMetrics {

    /** Committed assessments of one batch. */
    void assessed(List<RiskAssessment> assessments);

    void batchProcessed(int size, Duration elapsed);
}
