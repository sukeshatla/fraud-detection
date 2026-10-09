package com.fraudplatform.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.AlertResolvedEvent;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.alerts.support.IntegrationTest;
import com.fraudplatform.testing.KafkaTestConsumer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** fraud.alerts.v1 → alert queue → review → fraud.alert-resolutions.v1 */
@IntegrationTest
class AlertIngestionIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-007-01/09: alert from Kafka is queryable once (even if redelivered); resolving it publishes AlertResolvedEvent")
    void endToEnd() throws Exception {
        String account = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        FraudAlertEvent event = alert(account);
        String json = mapper.writeValueAsString(event);

        kafkaTemplate.send(Topics.FRAUD_ALERTS, account, json).get();
        kafkaTemplate.send(Topics.FRAUD_ALERTS, account, json).get(); // redelivery

        String url = "/api/v1/alerts/" + event.alertEventId();
        await().atMost(Duration.ofSeconds(30)).until(() -> mvc.get().uri(url).exchange().getResponse().getStatus() == 200);
        Thread.sleep(1000); // give the duplicate time to be (not) inserted
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert WHERE transaction_id = ?", Integer.class,
                event.transactionId())).isEqualTo(1);

        patch(url, "UNDER_REVIEW", 0);
        patch(url, "FALSE_POSITIVE", 1);

        AlertResolvedEvent resolved = mapper.readValue(KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.ALERT_RESOLUTIONS, account, 1).getFirst().value(),
                AlertResolvedEvent.class);
        assertThat(resolved.alertId()).isEqualTo(event.alertEventId());
        assertThat(resolved.resolution()).isEqualTo("FALSE_POSITIVE");
        assertThat(resolved.resolvedBy()).isEqualTo("analyst-7");
    }

    private void patch(String url, String status, long version) {
        assertThat(mvc.patch().uri(url).header("X-Actor", "analyst-7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\",\"version\":%d}".formatted(status, version)).exchange())
                .hasStatus(HttpStatus.OK);
    }

    private static FraudAlertEvent alert(String account) {
        Instant now = Instant.now();
        return new FraudAlertEvent(1, UUID.randomUUID(), UUID.randomUUID(), "txn-" + UUID.randomUUID(), account,
                new BigDecimal("9000.00"), "USD", "m-1", "7995", "MT", "CARD_NOT_PRESENT", now, 85, 90, "DECLINE",
                List.of(new FraudAlertEvent.RuleHit("HIGH_AMOUNT", 30, "big")), now, 0.91, "lr-v1");
    }
}
