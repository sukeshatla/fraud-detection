package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.util.Optional;

/** Offset pagination: page N of size S, with a total count. */
public record PageQuery(AlertStatus status, Optional<Severity> severity, int page, int size) {}
