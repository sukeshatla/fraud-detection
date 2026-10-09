package com.fraudplatform.alerts.application;

/** The client acted on an outdated version (layer 2 rejected us). Carries the current state. */
public class StaleAlertException extends RuntimeException {

    private final transient AlertView current;

    public StaleAlertException(AlertView current) {
        super("Alert %s was modified concurrently; current version is %d".formatted(current.id(), current.version()));
        this.current = current;
    }

    public AlertView current() {
        return current;
    }
}
