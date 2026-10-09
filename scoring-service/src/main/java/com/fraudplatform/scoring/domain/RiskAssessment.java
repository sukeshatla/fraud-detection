package com.fraudplatform.scoring.domain;

import com.fraudplatform.scoring.domain.ml.MlPrediction;
import com.fraudplatform.scoring.domain.ml.ScoreBlender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Final outcome for one transaction.
 *
 * @param ruleScore 0–100 from rules
 * @param riskScore 0–100 final score: rules blended with the model (Feature 006)
 * @param ml        model prediction, or null when the model was unavailable (rules-only fallback)
 */
public record RiskAssessment(
        Transaction transaction,
        int ruleScore,
        int riskScore,
        Decision decision,
        List<RuleHit> hits,
        Instant scoredAt,
        MlPrediction ml) {

    public RiskAssessment {
        hits = List.copyOf(hits);
    }

    /** Rules-only assessment (no model involved). */
    public RiskAssessment(Transaction transaction, int ruleScore, int riskScore, Decision decision, List<RuleHit> hits,
            Instant scoredAt) {
        this(transaction, ruleScore, riskScore, decision, hits, scoredAt, null);
    }

    public static RiskAssessment fromRules(Transaction transaction, RuleEvaluation rules, Instant scoredAt) {
        return of(transaction, rules, Optional.empty(), new ScoreBlender(1.0), scoredAt);
    }

    public static RiskAssessment of(Transaction transaction, RuleEvaluation rules, Optional<MlPrediction> ml,
            ScoreBlender blender, Instant scoredAt) {
        int riskScore = blender.blend(rules.score(), ml);
        return new RiskAssessment(transaction, rules.score(), riskScore, Decision.forScore(riskScore), rules.hits(),
                scoredAt, ml.orElse(null));
    }

    public Optional<MlPrediction> mlPrediction() {
        return Optional.ofNullable(ml);
    }

    public boolean raisesAlert() {
        return decision != Decision.APPROVE;
    }
}
