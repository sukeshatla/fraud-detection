package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.RiskAssessment;

/** Outbound port: hands a risky assessment to the alerting side. Must be durable before returning. */
public interface AlertPublisher {

    void publish(RiskAssessment assessment);
}
