package com.fraudplatform.alerts.domain;

import java.util.Map;
import java.util.Set;

/**
 * Alert workflow as an explicit state machine:
 * OPEN → UNDER_REVIEW → (OPEN | CONFIRMED_FRAUD | FALSE_POSITIVE). Resolved states are terminal.
 */
public enum AlertStatus {
    OPEN,
    UNDER_REVIEW,
    CONFIRMED_FRAUD,
    FALSE_POSITIVE;

    private static final Map<AlertStatus, Set<AlertStatus>> ALLOWED = Map.of(
            OPEN, Set.of(UNDER_REVIEW),
            UNDER_REVIEW, Set.of(OPEN, CONFIRMED_FRAUD, FALSE_POSITIVE),
            CONFIRMED_FRAUD, Set.of(),
            FALSE_POSITIVE, Set.of());

    public boolean canTransitionTo(AlertStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public void requireTransitionTo(AlertStatus next) {
        if (!canTransitionTo(next)) {
            throw new InvalidTransitionException(this, next);
        }
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
