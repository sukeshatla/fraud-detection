package com.fraudplatform.alerts.domain;

/** The requested status change is not allowed by the alert workflow. */
public class InvalidTransitionException extends RuntimeException {

    private final AlertStatus from;
    private final AlertStatus to;

    public InvalidTransitionException(AlertStatus from, AlertStatus to) {
        super("Cannot move an alert from %s to %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public AlertStatus from() {
        return from;
    }

    public AlertStatus to() {
        return to;
    }
}
