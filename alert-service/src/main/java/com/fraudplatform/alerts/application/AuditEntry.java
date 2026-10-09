package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import java.time.Instant;

/** One row of the alert's audit trail. */
public record AuditEntry(AlertStatus fromStatus, AlertStatus toStatus, String actor, Instant at) {}
