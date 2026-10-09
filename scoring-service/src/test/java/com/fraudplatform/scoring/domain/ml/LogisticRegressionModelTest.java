package com.fraudplatform.scoring.domain.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogisticRegressionModelTest {

    // 2 features: z = ((x0-1)/2)*3 + ((x1-0)/1)*(-1) + 0.5
    private final LogisticRegressionModel model = new LogisticRegressionModel("test-v1", List.of("a", "b"),
            new double[] {1, 0}, new double[] {2, 1}, new double[] {3, -1}, 0.5);

    @Test
    @DisplayName("AC-006-02: p = sigmoid(w · standardised(x) + b)")
    void computesProbability() {
        double z = ((5 - 1) / 2.0) * 3 + (2 - 0) * -1 + 0.5; // 4.5
        assertThat(model.predict(new double[] {5, 2})).isCloseTo(1 / (1 + Math.exp(-z)), within(1e-12));
    }

    @Test
    @DisplayName("Probability stays within [0, 1] even for extreme inputs (numerically stable sigmoid)")
    void extremeInputsStayBounded() {
        assertThat(model.predict(new double[] {1e6, 0})).isBetween(0.0, 1.0).isCloseTo(1.0, within(1e-9));
        assertThat(model.predict(new double[] {-1e6, 0})).isBetween(0.0, 1.0).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void rejectsWrongDimension() {
        assertThatThrownBy(() -> model.predict(new double[] {1})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInconsistentParameters() {
        assertThatThrownBy(() -> new LogisticRegressionModel("bad", List.of("a"), new double[] {0, 0},
                new double[] {1}, new double[] {1}, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
