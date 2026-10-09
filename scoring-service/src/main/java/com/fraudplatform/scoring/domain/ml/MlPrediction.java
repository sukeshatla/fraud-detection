package com.fraudplatform.scoring.domain.ml;

/** Model output: fraud probability in [0, 1] and the model version that produced it (for audit). */
public record MlPrediction(double probability, String modelVersion) {

    public MlPrediction {
        if (probability < 0 || probability > 1 || Double.isNaN(probability)) {
            throw new IllegalArgumentException("probability must be in [0, 1]: " + probability);
        }
    }
}
