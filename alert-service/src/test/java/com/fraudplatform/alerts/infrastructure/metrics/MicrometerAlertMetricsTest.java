package com.fraudplatform.alerts.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerAlertMetricsTest {

    @Test
    @DisplayName("AC-012-02: alerts raised by severity, resolutions by outcome, time-to-resolution")
    void records() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        MicrometerAlertMetrics metrics = new MicrometerAlertMetrics(meters);

        metrics.raised(Severity.HIGH);
        metrics.raised(Severity.HIGH);
        metrics.resolved(AlertStatus.FALSE_POSITIVE, Duration.ofMinutes(12));

        assertThat(meters.get("alerts_raised_total").tag("severity", "HIGH").counter().count()).isEqualTo(2);
        assertThat(meters.get("alert_resolutions_total").tag("resolution", "FALSE_POSITIVE").counter().count()).isEqualTo(1);
        assertThat(meters.get("alert_time_to_resolution").timer().max(TimeUnit.MINUTES)).isEqualTo(12);
    }
}
