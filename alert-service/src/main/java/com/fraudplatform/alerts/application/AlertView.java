package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model of an alert, including the transaction snapshot and the rule hits that raised it. */
public record AlertView(
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
        AlertStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<RuleHitView> ruleHits) {

    public AlertView {
        ruleHits = List.copyOf(ruleHits);
    }

    /** View of a just-created alert: OPEN, version 0. */
    public static AlertView fromNew(NewAlert a) {
        return new AlertView(a.id(), a.transactionId(), a.accountId(), a.amount(), a.currency(), a.merchantId(),
                a.merchantCategoryCode(), a.country(), a.channel(), a.occurredAt(), a.ruleScore(), a.riskScore(),
                a.mlProbability(), a.modelVersion(), a.decision(), a.severity(), AlertStatus.OPEN, 0, a.createdAt(),
                a.createdAt(), a.ruleHits());
    }
}
