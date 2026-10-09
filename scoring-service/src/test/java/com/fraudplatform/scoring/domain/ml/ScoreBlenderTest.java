package com.fraudplatform.scoring.domain.ml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ScoreBlenderTest {

    private final ScoreBlender blender = new ScoreBlender(0.6);

    @ParameterizedTest(name = "rule {0}, p {1} → {2}")
    @CsvSource({
            "0,   0.95, 38",   // ML alone can push a rule-clean txn towards REVIEW
            "40,  0.90, 60",   // 0.6·40 + 0.4·90 = 60 → escalated
            "85,  0.01, 85",   // ML never dilutes a rule decision: max(85, 51.4)
            "40,  0.40, 40",   // blend 40 = rule
            "100, 1.00, 100"
    })
    @DisplayName("AC-006-03: final = max(rule, round(0.6·rule + 0.4·100p))")
    void blends(int rule, double p, int expected) {
        assertThat(blender.blend(rule, Optional.of(new MlPrediction(p, "v1")))).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-006-05: ML unavailable → rules only")
    void fallsBackToRules() {
        assertThat(blender.blend(55, Optional.empty())).isEqualTo(55);
    }
}
