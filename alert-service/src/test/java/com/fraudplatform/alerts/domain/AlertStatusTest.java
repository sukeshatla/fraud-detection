package com.fraudplatform.alerts.domain;

import static com.fraudplatform.alerts.domain.AlertStatus.CONFIRMED_FRAUD;
import static com.fraudplatform.alerts.domain.AlertStatus.FALSE_POSITIVE;
import static com.fraudplatform.alerts.domain.AlertStatus.OPEN;
import static com.fraudplatform.alerts.domain.AlertStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlertStatusTest {

    @ParameterizedTest(name = "{0} → {1} allowed")
    @CsvSource({"OPEN,UNDER_REVIEW", "UNDER_REVIEW,OPEN", "UNDER_REVIEW,CONFIRMED_FRAUD", "UNDER_REVIEW,FALSE_POSITIVE"})
    @DisplayName("AC-007-04: allowed transitions")
    void allowed(AlertStatus from, AlertStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
        from.requireTransitionTo(to); // does not throw
    }

    @ParameterizedTest(name = "{0} → {1} rejected")
    @CsvSource({"OPEN,CONFIRMED_FRAUD", "OPEN,FALSE_POSITIVE", "OPEN,OPEN", "CONFIRMED_FRAUD,OPEN",
            "FALSE_POSITIVE,UNDER_REVIEW", "CONFIRMED_FRAUD,FALSE_POSITIVE"})
    @DisplayName("AC-007-04: everything else is an invalid transition")
    void rejected(AlertStatus from, AlertStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.requireTransitionTo(to)).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void terminalStates() {
        assertThat(CONFIRMED_FRAUD.isTerminal()).isTrue();
        assertThat(FALSE_POSITIVE.isTerminal()).isTrue();
        assertThat(OPEN.isTerminal()).isFalse();
        assertThat(UNDER_REVIEW.isTerminal()).isFalse();
    }
}
