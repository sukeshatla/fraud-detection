package com.fraudplatform.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to {@link com.fraudplatform.contracts.Topics#FRAUD_ALERTS} when a transaction scores
 * {@code REVIEW} or {@code DECLINE}. Record key: {@code accountId}.
 *
 * <p>Carries a snapshot of the transaction so the alert service needs no lookup back into scoring
 * (event-carried state transfer). Evolution rules as for {@link TransactionReceivedEvent}: additive only.
 *
 * @param alertEventId  unique id of this alert event
 * @param sourceEventId {@code eventId} of the {@link TransactionReceivedEvent} that was scored
 * @param ruleScore     0–100 from deterministic rules
 * @param riskScore     0–100 final score (equals ruleScore until ML blending, Feature 006)
 * @param decision      APPROVE | REVIEW | DECLINE
 * @param ruleHits      which rules fired and why (explainability)
 * @param mlProbability model fraud probability, or null if scored rules-only (added in v1.1, optional)
 * @param modelVersion  model that produced {@code mlProbability}, or null (added in v1.1, optional)
 */
public record FraudAlertEvent(
        int schemaVersion,
        UUID alertEventId,
        UUID sourceEventId,
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
        String decision,
        List<RuleHit> ruleHits,
        Instant scoredAt,
        Double mlProbability,
        String modelVersion) {

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE = "FraudAlertRaised";

    public record RuleHit(String code, int weight, String reason) {}
}
