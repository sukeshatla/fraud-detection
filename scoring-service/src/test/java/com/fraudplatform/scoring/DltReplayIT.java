package com.fraudplatform.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.scoring.application.DeadLetterReplay;
import com.fraudplatform.scoring.support.IntegrationTest;
import com.fraudplatform.testing.KafkaTestConsumer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** AC-010-03: a record that died while a dependency was down is scored after replay. */
@IntegrationTest
class DltReplayIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private DeadLetterReplay replay;

    @Test
    @DisplayName("AC-010-03: dead-lettered transaction → replay → scored and alerted")
    void replayedDeadLetterIsScored() throws Exception {
        String account = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        var event = new TransactionReceivedEvent(1, UUID.randomUUID(), "txn-" + UUID.randomUUID(), account,
                new BigDecimal("9000.00"), "USD", "m-1", "7995", "US", "CARD_NOT_PRESENT", now, now);
        // as if it had failed earlier because Redis was down
        kafkaTemplate.send(Topics.TRANSACTIONS_RECEIVED + ".DLT", account, mapper.writeValueAsString(event)).get();

        assertThat(replay.replay(1000)).isPositive();

        FraudAlertEvent alert = mapper.readValue(KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.FRAUD_ALERTS, account, 1).getFirst().value(), FraudAlertEvent.class);
        assertThat(alert.transactionId()).isEqualTo(event.transactionId());
    }
}
