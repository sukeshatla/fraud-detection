package com.fraudplatform.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.scoring.support.IntegrationTest;
import com.fraudplatform.testing.KafkaTestConsumer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** Feature 003 end-to-end: transactions.received.v1 → scoring (Redis windows, rules) → fraud.alerts.v1 / DLT. */
@IntegrationTest
class ScoringPipelineIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("AC-003-03/10: 6 transactions in a minute → VELOCITY alert on fraud.alerts.v1")
    void velocityBurstRaisesAlert() throws Exception {
        String account = uniqueAccount();
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 6; i++) {
            send(event(UUID.randomUUID(), account, "25.00", "5411", start.plusSeconds(i)));
        }

        FraudAlertEvent alert = alertsFor(account, 1).getFirst();

        assertThat(alert.decision()).isEqualTo("REVIEW");
        assertThat(alert.ruleHits()).extracting(FraudAlertEvent.RuleHit::code).containsExactly("VELOCITY");
        assertThat(alert.riskScore()).isEqualTo(40);
    }

    @Test
    @DisplayName("AC-003-08: the same eventId delivered twice raises exactly one alert")
    void duplicateDeliveryRaisesOneAlert() throws Exception {
        String account = uniqueAccount();
        TransactionReceivedEvent risky = event(UUID.randomUUID(), account, "9000.00", "7995", Instant.now());

        send(risky);
        send(risky);

        assertThat(KafkaTestConsumer.recordsWithin(kafka.getBootstrapServers(), Topics.FRAUD_ALERTS, account, Duration.ofSeconds(5)))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-003-09: a poison pill goes to the DLT and the partition keeps flowing")
    void poisonPillGoesToDlt() throws Exception {
        String account = uniqueAccount();

        kafkaTemplate.send(Topics.TRANSACTIONS_RECEIVED, account, "{ this is not json").get();
        send(event(UUID.randomUUID(), account, "9000.00", "7995", Instant.now())); // same key → same partition, after the pill

        ConsumerRecord<String, String> dead = KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.TRANSACTIONS_RECEIVED + ".DLT", account, 1).getFirst();
        assertThat(dead.value()).isEqualTo("{ this is not json");
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-exception-fqcn").value(), StandardCharsets.UTF_8))
                .contains("ListenerExecutionFailedException");
        assertThat(alertsFor(account, 1)).hasSize(1); // the valid record behind the pill was still scored
    }

    private void send(TransactionReceivedEvent event) throws Exception {
        kafkaTemplate.send(Topics.TRANSACTIONS_RECEIVED, event.accountId(), mapper.writeValueAsString(event)).get();
    }

    private List<FraudAlertEvent> alertsFor(String account, int expected) {
        return KafkaTestConsumer.awaitRecords(kafka.getBootstrapServers(), Topics.FRAUD_ALERTS, account, expected).stream()
                .map(r -> mapper.readValue(r.value(), FraudAlertEvent.class))
                .toList();
    }

    private static TransactionReceivedEvent event(UUID eventId, String account, String amount, String mcc, Instant occurredAt) {
        return new TransactionReceivedEvent(1, eventId, "txn-" + UUID.randomUUID(), account, new BigDecimal(amount), "USD",
                "m-1", mcc, "US", "CARD_NOT_PRESENT", occurredAt, occurredAt);
    }

    private static String uniqueAccount() {
        return "acc-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
