package com.fraudplatform.alerts.application;

/** Use case: an alert arrives from scoring. Idempotent: redelivered events are no-ops. */
public class IngestAlertService {

    private final AlertRepository repository;
    private final AlertChangeBus changes;

    public IngestAlertService(AlertRepository repository, AlertChangeBus changes) {
        this.repository = repository;
        this.changes = changes;
    }

    /** @return true if a new alert was created, false if it already existed */
    public boolean ingest(NewAlert alert) {
        boolean created = repository.insertIfAbsent(alert);
        if (created) {
            changes.publish(new AlertChange(AlertChange.Type.CREATED, AlertView.fromNew(alert)));
        }
        return created;
    }
}
