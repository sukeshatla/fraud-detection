package com.fraudplatform.alerts;

import static com.fraudplatform.alerts.application.AlertFixtures.newAlert;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.testing.TestJwts;
import com.fraudplatform.alerts.support.IntegrationTest;
import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.testing.KafkaTestConsumer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;

/** Feature 012 in alert-service. */
@IntegrationTest
class AlertObservabilityIT {

    private static final String SUPERVISOR = TestJwts.bearer(TestJwts.user("supervisor-7", "ANALYST", "SUPERVISOR"));

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AlertRepository repository;

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("AC-012-04/05: the analyst's request id and the PATCH's trace context travel on the resolution event (through the outbox)")
    void resolutionCarriesRequestContext() {
        UUID id = UUID.randomUUID();
        String account = "acc-obs-" + id.toString().substring(0, 8);
        repository.insertIfAbsent(newAlert(id, "txn-" + id, account, Instant.now()));
        patch(id, "UNDER_REVIEW", 0, "rid-review-1");

        var result = patch(id, "FALSE_POSITIVE", 1, "rid-resolve-1");

        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("rid-resolve-1");
        ConsumerRecord<String, String> event = KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.ALERT_RESOLUTIONS, account, 1).getFirst();
        assertThat(header(event, EventHeaders.REQUEST_ID)).isEqualTo("rid-resolve-1");
        // captured from the PATCH's server span when the outbox row was written, not the relay's
        assertThat(header(event, EventHeaders.TRACEPARENT)).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    }

    @Test
    @DisplayName("AC-012-02: alert metrics are exposed for Prometheus")
    void prometheusScrape() throws Exception {
        String body = mvc.get().uri("/actuator/prometheus").exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("alerts_raised_total", "alert_resolutions_total", "alert_time_to_resolution_seconds",
                "outbox_relayed_total", "http_server_requests_seconds_bucket");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult patch(UUID id, String status, long version, String requestId) {
        var result = mvc.patch().uri("/api/v1/alerts/" + id).header("X-Request-Id", requestId)
                .header("Authorization", SUPERVISOR)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\",\"version\":%d}".formatted(status, version))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return result;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
