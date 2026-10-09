package com.fraudplatform.alerts.application;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(UUID id) {
        super("Alert " + id + " not found");
    }
}
