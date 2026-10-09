package com.fraudplatform.scoring.domain;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleEngineTest {

    private static FraudRule firing(String code, int weight) {
        return new FraudRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
                return Optional.of(new RuleHit(code, weight, code + " fired"));
            }
        };
    }

    private static FraudRule silent(String code) {
        return new FraudRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
                return Optional.empty();
            }
        };
    }

    @Test
    @DisplayName("AC-003-01: every rule is evaluated; hits are collected with their reasons")
    void collectsHitsFromAllRules() {
        RuleEngine engine = new RuleEngine(List.of(firing("A", 10), silent("B"), firing("C", 25)));

        RuleEvaluation result = engine.evaluate(aTransaction().build(), AccountActivity.none());

        assertThat(result.hits()).extracting(RuleHit::code).containsExactly("A", "C");
        assertThat(result.score()).isEqualTo(35);
    }

    @Test
    @DisplayName("AC-003-07: score is the sum of weights, capped at 100")
    void scoreIsCappedAt100() {
        RuleEngine engine = new RuleEngine(List.of(firing("A", 60), firing("B", 70)));

        assertThat(engine.evaluate(aTransaction().build(), AccountActivity.none()).score()).isEqualTo(100);
    }

    @Test
    @DisplayName("No rules fire → score 0, no hits")
    void cleanTransactionScoresZero() {
        RuleEngine engine = new RuleEngine(List.of(silent("A")));

        RuleEvaluation result = engine.evaluate(aTransaction().build(), AccountActivity.none());

        assertThat(result.score()).isZero();
        assertThat(result.hits()).isEmpty();
    }

    @ParameterizedTest(name = "score {0} → {1}")
    @CsvSource({"0,APPROVE", "39,APPROVE", "40,REVIEW", "74,REVIEW", "75,DECLINE", "100,DECLINE"})
    @DisplayName("AC-003-07: decision bands <40 APPROVE, 40–74 REVIEW, ≥75 DECLINE")
    void decisionBands(int score, Decision expected) {
        assertThat(Decision.forScore(score)).isEqualTo(expected);
    }
}
