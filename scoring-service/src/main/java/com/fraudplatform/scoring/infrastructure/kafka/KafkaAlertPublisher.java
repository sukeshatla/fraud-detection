package com.fraudplatform.scoring.infrastructure.kafka;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import com.fraudplatform.scoring.application.AlertPublisher;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.Transaction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes {@link FraudAlertEvent}s keyed by accountId and waits for the broker ack, so the
 * listener only completes (and the offset is only committed) once the alert is durable.
 */
public class KafkaAlertPublisher implements AlertPublisher {

    private final KafkaTemplate<String, String> template;
    private final JsonMapper mapper;
    private final String topic;
    private final Duration timeout;

    public KafkaAlertPublisher(KafkaTemplate<String, String> template, JsonMapper mapper, String topic, Duration timeout) {
        this.template = template;
        this.mapper = mapper;
        this.topic = topic;
        this.timeout = timeout;
    }

    @Override
    public void publish(RiskAssessment assessment) {
        FraudAlertEvent event = toEvent(assessment);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.accountId(), mapper.writeValueAsString(event));
        record.headers()
                .add(EventHeaders.EVENT_TYPE, FraudAlertEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8))
                .add(EventHeaders.SCHEMA_VERSION, String.valueOf(FraudAlertEvent.SCHEMA_VERSION).getBytes(StandardCharsets.UTF_8));
        try {
            template.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Kafka rejected alert for " + event.transactionId(), e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Kafka did not acknowledge alert within " + timeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing alert", e);
        }
    }

    private static FraudAlertEvent toEvent(RiskAssessment a) {
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
                a.scoredAt());
    }
}
