package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.time.Duration;

/** Outbound port: alerting business metrics (keeps the use cases free of a metrics library). */
public interface AlertMetrics {

    void raised(Severity severity);

    void resolved(AlertStatus resolution, Duration timeToResolution);
}
