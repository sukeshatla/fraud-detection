package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.RiskAssessment;
import java.util.List;

/**
 * Outbound port: durably stores assessments and enqueues the alerts they raise, <b>atomically</b>
 * (transactional outbox: both commit or neither does). Idempotent on replay.
 */
public interface AssessmentRepository {

    void saveAll(List<RiskAssessment> assessments, List<RiskAssessment> alerts);
}
