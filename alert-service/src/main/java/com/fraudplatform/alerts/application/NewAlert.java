package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An alert to create from a FraudAlertEvent. */
public record NewAlert(
        UUID id,
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategoryCode,
        String country,
        String channel,
        Instant occurredAt,
        int ruleScore,
        int riskScore,
        BigDecimal mlProbability,
        String modelVersion,
        String decision,
        Severity severity,
        Instant createdAt,
        List<RuleHitView> ruleHits) {

    public NewAlert {
        ruleHits = List.copyOf(ruleHits);
    }
}
