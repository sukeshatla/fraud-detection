package com.fraudplatform.scoring.domain;

import java.time.Instant;
import java.util.List;

/**
 * Final outcome for one transaction.
 *
 * @param ruleScore 0–100 from rules
 * @param riskScore 0–100 final score; equals ruleScore until ML blending (Feature 006)
 */
public record RiskAssessment(
        Transaction transaction,
        int ruleScore,
        int riskScore,
        Decision decision,
        List<RuleHit> hits,
        Instant scoredAt) {

    public RiskAssessment {
        hits = List.copyOf(hits);
    }

    public static RiskAssessment fromRules(Transaction transaction, RuleEvaluation rules, Instant scoredAt) {
        return new RiskAssessment(transaction, rules.score(), rules.score(), Decision.forScore(rules.score()),
                rules.hits(), scoredAt);
    }

    public boolean raisesAlert() {
        return decision != Decision.APPROVE;
    }
}
