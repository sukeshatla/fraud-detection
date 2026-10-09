package com.fraudplatform.scoring.domain;

/** What should happen to the transaction, derived from the 0–100 risk score. */
public enum Decision {
    APPROVE,
    REVIEW,
    DECLINE;

    static final int REVIEW_THRESHOLD = 40;
    static final int DECLINE_THRESHOLD = 75;

    public static Decision forScore(int score) {
        if (score >= DECLINE_THRESHOLD) {
            return DECLINE;
        }
        return score >= REVIEW_THRESHOLD ? REVIEW : APPROVE;
    }
}
