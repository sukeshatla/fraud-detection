package com.fraudplatform.alerts.infrastructure.persistence;

import com.fraudplatform.alerts.domain.AlertStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Audit row: one per status change, in the same transaction as the change. */
@Entity
@Table(name = "alert_event")
public class AlertEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID alertId;

    @Enumerated(EnumType.STRING)
    private AlertStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private AlertStatus toStatus;

    private String actor;
    private Instant changedAt;

    protected AlertEventEntity() {} // JPA

    AlertEventEntity(UUID alertId, AlertStatus fromStatus, AlertStatus toStatus, String actor, Instant changedAt) {
        this.alertId = alertId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.changedAt = changedAt;
    }

    public AlertStatus getFromStatus() { return fromStatus; }
    public AlertStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public Instant getChangedAt() { return changedAt; }
}
