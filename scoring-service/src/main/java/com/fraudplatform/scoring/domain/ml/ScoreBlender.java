package com.fraudplatform.scoring.domain.ml;

import java.util.Optional;

/**
 * Combines the rule score with the model's probability.
 *
 * <p>{@code final = max(rule, round(w·rule + (1−w)·100·p))}. The {@code max} means the model can
 * <b>escalate</b> a transaction the rules missed, but can never <b>dilute</b> a rule hit. Explainable
 * rules keep their authority over an opaque model. Without a prediction (fallback) the rule score
 * stands alone.
 */
public class ScoreBlender {

    private final double ruleWeight;

    public ScoreBlender(double ruleWeight) {
        if (ruleWeight < 0 || ruleWeight > 1) {
            throw new IllegalArgumentException("ruleWeight must be in [0, 1]");
        }
        this.ruleWeight = ruleWeight;
    }

    public int blend(int ruleScore, Optional<MlPrediction> prediction) {
        return prediction
                .map(p -> (int) Math.round(ruleWeight * ruleScore + (1 - ruleWeight) * 100 * p.probability()))
                .map(blended -> Math.max(ruleScore, blended))
                .orElse(ruleScore);
    }
}
