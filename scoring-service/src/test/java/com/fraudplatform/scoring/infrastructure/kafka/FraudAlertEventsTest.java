package com.fraudplatform.scoring.infrastructure.kafka;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.messaging.outbox.OutboxMessage;
import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleHit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FraudAlertEventsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final FraudAlertEvents events = new FraudAlertEvents(mapper, "fraud.alerts.v1");

    private final RiskAssessment assessment = new RiskAssessment(
            aTransaction().amount("7500.00").mcc("7995").build(), 50, 50, Decision.REVIEW,
            List.of(new RuleHit("HIGH_AMOUNT", 30, "big"), new RuleHit("HIGH_RISK_MCC", 20, "gambling")),
            Instant.parse("2026-10-09T18:15:31Z"));

    @Test
    @DisplayName("AC-003-10: outbox message targets fraud.alerts.v1, keyed by accountId, carrying the v1 contract + headers")
    void mapsToContract() {
        OutboxMessage message = events.toOutbox(assessment);
        FraudAlertEvent event = mapper.readValue(message.payload(), FraudAlertEvent.class);

        assertThat(message.topic()).isEqualTo("fraud.alerts.v1");
        assertThat(message.key()).isEqualTo("acc-1001");
        assertThat(message.headers()).containsEntry(EventHeaders.EVENT_TYPE, "FraudAlertRaised").containsEntry(EventHeaders.SCHEMA_VERSION, "1");
        assertThat(event.transactionId()).isEqualTo("txn-1");
        assertThat(event.sourceEventId()).isEqualTo(assessment.transaction().eventId());
        assertThat(event.riskScore()).isEqualTo(50);
        assertThat(event.decision()).isEqualTo("REVIEW");
        assertThat(event.ruleHits()).extracting(FraudAlertEvent.RuleHit::code).containsExactly("HIGH_AMOUNT", "HIGH_RISK_MCC");
    }

    @Test
    @DisplayName("Redelivery produces the same alertEventId (deterministic) so downstream can dedupe")
    void alertIdIsDeterministic() {
        assertThat(FraudAlertEvents.toEvent(assessment).alertEventId()).isEqualTo(FraudAlertEvents.toEvent(assessment).alertEventId());
    }
}
