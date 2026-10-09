package com.fraudplatform.scoring.infrastructure.kafka;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleHit;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaAlertPublisherTest {

    @Mock
    private KafkaTemplate<String, String> template;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final RiskAssessment assessment = new RiskAssessment(
            aTransaction().amount("7500.00").mcc("7995").build(), 50, 50, Decision.REVIEW,
            List.of(new RuleHit("HIGH_AMOUNT", 30, "big"), new RuleHit("HIGH_RISK_MCC", 20, "gambling")),
            Instant.parse("2026-10-09T18:15:31Z"));

    @Test
    @DisplayName("AC-003-10: alert goes to fraud.alerts.v1 keyed by accountId with score, decision and hits")
    void publishesAlertContract() {
        given(template.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        new KafkaAlertPublisher(template, mapper, "fraud.alerts.v1", Duration.ofSeconds(1)).publish(assessment);

        ProducerRecord<String, String> record = captured();
        FraudAlertEvent event = mapper.readValue(record.value(), FraudAlertEvent.class);
        assertThat(record.topic()).isEqualTo("fraud.alerts.v1");
        assertThat(record.key()).isEqualTo("acc-1001");
        assertThat(event.transactionId()).isEqualTo("txn-1");
        assertThat(event.sourceEventId()).isEqualTo(assessment.transaction().eventId());
        assertThat(event.riskScore()).isEqualTo(50);
        assertThat(event.decision()).isEqualTo("REVIEW");
        assertThat(event.ruleHits()).extracting(FraudAlertEvent.RuleHit::code).containsExactly("HIGH_AMOUNT", "HIGH_RISK_MCC");
    }

    @Test
    @DisplayName("Redelivery produces the same alertEventId (deterministic) so downstream can dedupe")
    void alertIdIsDeterministic() {
        given(template.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
        KafkaAlertPublisher publisher = new KafkaAlertPublisher(template, mapper, "fraud.alerts.v1", Duration.ofSeconds(1));

        publisher.publish(assessment);
        publisher.publish(assessment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(r -> mapper.readValue(r.value(), FraudAlertEvent.class).alertEventId())
                .containsOnly(mapper.readValue(captor.getAllValues().getFirst().value(), FraudAlertEvent.class).alertEventId());
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> captured() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }
}
