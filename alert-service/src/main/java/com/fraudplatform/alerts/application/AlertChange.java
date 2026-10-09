package com.fraudplatform.alerts.application;

/** Something about an alert changed. Pushed to live dashboards. */
public record AlertChange(Type type, AlertView alert) {

    public enum Type {
        CREATED,
        UPDATED
    }
}
