package com.fraudplatform.alerts.domain;

/** Queue priority. Policy of the alerting domain, derived from scoring's decision and score. */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH;

    static final int MEDIUM_THRESHOLD = 60;

    public static Severity of(String decision, int riskScore) {
        if ("DECLINE".equals(decision)) {
            return HIGH;
        }
        return riskScore >= MEDIUM_THRESHOLD ? MEDIUM : LOW;
    }
}
