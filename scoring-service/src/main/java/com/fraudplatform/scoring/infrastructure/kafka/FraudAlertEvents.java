package com.fraudplatform.scoring.infrastructure.kafka;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.messaging.outbox.OutboxMessage;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.Transaction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps an assessment to the {@code fraud.alerts.v1} contract, as an outbox message keyed by
 * accountId. The relay publishes it after the surrounding transaction commits.
 */
public class FraudAlertEvents {

    private final JsonMapper mapper;
    private final String topic;

    public FraudAlertEvents(JsonMapper mapper, String topic) {
        this.mapper = mapper;
        this.topic = topic;
    }

    public OutboxMessage toOutbox(RiskAssessment assessment) {
        FraudAlertEvent event = toEvent(assessment);
        return new OutboxMessage(topic, event.accountId(), mapper.writeValueAsString(event), Map.of(
                EventHeaders.EVENT_TYPE, FraudAlertEvent.EVENT_TYPE,
                EventHeaders.SCHEMA_VERSION, String.valueOf(FraudAlertEvent.SCHEMA_VERSION)));
    }

    static FraudAlertEvent toEvent(RiskAssessment a) {
        Transaction tx = a.transaction();
        return new FraudAlertEvent(
                FraudAlertEvent.SCHEMA_VERSION,
                // Deterministic: a redelivered event yields the same alert id, so consumers can dedupe.
                UUID.nameUUIDFromBytes(("alert:" + tx.eventId()).getBytes(StandardCharsets.UTF_8)),
                tx.eventId(),
                tx.transactionId(),
                tx.accountId(),
                tx.amount(),
                tx.currency(),
                tx.merchantId(),
                tx.merchantCategoryCode(),
                tx.country(),
                tx.channel(),
                tx.occurredAt(),
                a.ruleScore(),
                a.riskScore(),
                a.decision().name(),
                a.hits().stream().map(h -> new FraudAlertEvent.RuleHit(h.code(), h.weight(), h.reason())).toList(),
                a.scoredAt(),
                a.mlPrediction().map(p -> p.probability()).orElse(null),
                a.mlPrediction().map(p -> p.modelVersion()).orElse(null));
    }
}
