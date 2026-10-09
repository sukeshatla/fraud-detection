package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.util.Optional;

/** Keyset ("seek") pagination: the next {@code size} alerts strictly after {@code after}. */
public record KeysetQuery(AlertStatus status, Optional<Severity> severity, Optional<Cursor> after, int size) {}
