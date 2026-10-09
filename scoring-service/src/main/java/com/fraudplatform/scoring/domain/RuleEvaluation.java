package com.fraudplatform.scoring.domain;

import java.util.List;

/** Raw output of the rule engine: 0–100 score and the hits that produced it. */
public record RuleEvaluation(int score, List<RuleHit> hits) {

    public RuleEvaluation {
        hits = List.copyOf(hits);
    }
}
