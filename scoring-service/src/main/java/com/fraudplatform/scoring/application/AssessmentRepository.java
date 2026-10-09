package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.RiskAssessment;
import java.util.List;

/** Outbound port: durably stores assessments. Must be atomic per call and idempotent on replay. */
public interface AssessmentRepository {

    void saveAll(List<RiskAssessment> assessments);
}
