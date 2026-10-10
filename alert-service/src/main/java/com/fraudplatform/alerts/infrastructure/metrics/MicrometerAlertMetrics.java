package com.fraudplatform.alerts.infrastructure.metrics;

import com.fraudplatform.alerts.application.AlertMetrics;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * {@code alerts_raised_total{severity}}, {@code alert_resolutions_total{resolution}} (the
 * false-positive ratio is the key model-quality signal), and {@code alert_time_to_resolution_seconds}
 * (analyst queue health).
 */
public class MicrometerAlertMetrics implements AlertMetrics {

    private final MeterRegistry meters;
    private final Timer timeToResolution;

    public MicrometerAlertMetrics(MeterRegistry meters) {
        this.meters = meters;
        this.timeToResolution = Timer.builder("alert_time_to_resolution")
                .serviceLevelObjectives(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4))
                .register(meters);
    }

    @Override
    public void raised(Severity severity) {
        Counter.builder("alerts_raised_total").tag("severity", severity.name()).register(meters).increment();
    }

    @Override
    public void resolved(AlertStatus resolution, Duration duration) {
        Counter.builder("alert_resolutions_total").tag("resolution", resolution.name()).register(meters).increment();
        timeToResolution.record(duration);
    }
}
