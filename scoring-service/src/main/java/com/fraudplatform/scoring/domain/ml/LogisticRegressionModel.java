package com.fraudplatform.scoring.domain.ml;

import java.util.List;

/**
 * Immutable logistic-regression model: {@code p = σ(Σ wᵢ·(xᵢ − μᵢ)/σᵢ + b)}.
 * Thread-safe (no mutable state), so one instance serves every scoring thread.
 */
public final class LogisticRegressionModel {

    private final String version;
    private final List<String> featureNames;
    private final double[] means;
    private final double[] stds;
    private final double[] weights;
    private final double intercept;

    public LogisticRegressionModel(String version, List<String> featureNames, double[] means, double[] stds,
            double[] weights, double intercept) {
        int n = featureNames.size();
        if (means.length != n || stds.length != n || weights.length != n) {
            throw new IllegalArgumentException("means, stds and weights must each have " + n + " entries");
        }
        this.version = version;
        this.featureNames = List.copyOf(featureNames);
        this.means = means.clone();
        this.stds = stds.clone();
        this.weights = weights.clone();
        this.intercept = intercept;
    }

    public double predict(double[] features) {
        if (features.length != weights.length) {
            throw new IllegalArgumentException("expected " + weights.length + " features, got " + features.length);
        }
        double z = intercept;
        for (int i = 0; i < weights.length; i++) {
            z += weights[i] * (features[i] - means[i]) / stds[i];
        }
        return sigmoid(z);
    }

    /** Numerically stable: never computes exp of a large positive number. */
    static double sigmoid(double z) {
        if (z >= 0) {
            return 1 / (1 + Math.exp(-z));
        }
        double e = Math.exp(z);
        return e / (1 + e);
    }

    public String version() {
        return version;
    }

    public List<String> featureNames() {
        return featureNames;
    }
}
