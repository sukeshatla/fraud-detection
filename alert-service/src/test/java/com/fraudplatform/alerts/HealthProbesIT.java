package com.fraudplatform.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * AC-011-05: the probes the load balancer / orchestrator rely on. Liveness = "restart me if
 * DOWN"; readiness = "send me traffic". On shutdown Spring Boot flips readiness to
 * REFUSING_TRAFFIC first, so the LB drains the instance before graceful shutdown completes.
 */
@IntegrationTest
class HealthProbesIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private ApplicationAvailability availability;

    @Test
    @DisplayName("AC-011-05: readiness and liveness are UP; refusing traffic turns readiness OUT_OF_SERVICE (503)")
    void probes() {
        assertThat(mvc.get().uri("/actuator/health/liveness").exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).hasStatus(HttpStatus.OK);

        // What Spring Boot publishes when shutdown begins:
        AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);
        try {
            assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(mvc.get().uri("/actuator/health/liveness").exchange()).hasStatus(HttpStatus.OK); // drain, don't restart
        } finally {
            AvailabilityChangeEvent.publish(events, this, ReadinessState.ACCEPTING_TRAFFIC);
        }
        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }
}
