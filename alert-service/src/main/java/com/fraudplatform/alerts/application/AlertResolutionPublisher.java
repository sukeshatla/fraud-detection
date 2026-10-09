package com.fraudplatform.alerts.application;

/** Outbound port: tells the rest of the platform an alert was resolved. */
public interface AlertResolutionPublisher {

    void publish(AlertResolution resolution);
}
