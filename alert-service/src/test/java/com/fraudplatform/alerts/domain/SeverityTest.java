package com.fraudplatform.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeverityTest {

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource({"DECLINE,75,HIGH", "DECLINE,100,HIGH", "REVIEW,60,MEDIUM", "REVIEW,74,MEDIUM", "REVIEW,59,LOW", "REVIEW,40,LOW"})
    @DisplayName("Queue severity: DECLINE → HIGH; REVIEW ≥ 60 → MEDIUM; else LOW")
    void derivesSeverity(String decision, int riskScore, Severity expected) {
        assertThat(Severity.of(decision, riskScore)).isEqualTo(expected);
    }
}
