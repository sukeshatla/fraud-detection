package com.fraudplatform.scoring.domain;

import java.util.List;
import java.util.Optional;

/** Runs every rule and sums the weights of those that fire, capped at {@value #MAX_SCORE}. */
public class RuleEngine {

    static final int MAX_SCORE = 100;

    private final List<FraudRule> rules;

    public RuleEngine(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RuleEvaluation evaluate(Transaction transaction, AccountActivity activity) {
        List<RuleHit> hits = rules.stream()
                .map(rule -> rule.evaluate(transaction, activity))
                .flatMap(Optional::stream)
                .toList();
        int score = Math.min(MAX_SCORE, hits.stream().mapToInt(RuleHit::weight).sum());
        return new RuleEvaluation(score, hits);
    }

    public List<FraudRule> rules() {
        return rules;
    }
}
