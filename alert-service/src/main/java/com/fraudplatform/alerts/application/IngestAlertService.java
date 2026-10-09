package com.fraudplatform.alerts.application;

/** Use case: an alert arrives from scoring. Idempotent: redelivered events are no-ops. */
public class IngestAlertService {

    private final AlertRepository repository;

    public IngestAlertService(AlertRepository repository) {
        this.repository = repository;
    }

    /** @return true if a new alert was created, false if it already existed */
    public boolean ingest(NewAlert alert) {
        return repository.insertIfAbsent(alert);
    }
}
