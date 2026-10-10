package com.fraudplatform.alerts.application;

/** Use case: an alert arrives from scoring. Idempotent: redelivered events are no-ops. */
public class IngestAlertService {

    private final AlertRepository repository;
    private final AlertChangeBus changes;
    private final AlertMetrics metrics;

    public IngestAlertService(AlertRepository repository, AlertChangeBus changes, AlertMetrics metrics) {
        this.repository = repository;
        this.changes = changes;
        this.metrics = metrics;
    }

    /** @return true if a new alert was created, false if it already existed */
    public boolean ingest(NewAlert alert) {
        boolean created = repository.insertIfAbsent(alert);
        if (created) {
            changes.publish(new AlertChange(AlertChange.Type.CREATED, AlertView.fromNew(alert)));
            metrics.raised(alert.severity());
        }
        return created;
    }
}
