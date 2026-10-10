package com.fraudplatform.ingestion.infrastructure.kafka;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.ingestion.application.EventPublishingException;
import com.fraudplatform.ingestion.application.TransactionPublisher;
import com.fraudplatform.ingestion.domain.ReceivedTransaction;
import com.fraudplatform.ingestion.domain.Transaction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka adapter for {@link TransactionPublisher}.
 *
 * <ul>
 *   <li><b>Key = accountId</b>: every event for an account goes to one partition, so it is consumed
 *       in order. Velocity rules depend on that.
 *   <li><b>Synchronous ack</b>: blocks until the broker confirms ({@code acks=all}). On a virtual
 *       thread, blocking parks the virtual thread and frees its carrier.
 *   <li><b>Plain JSON string</b>: no Java type headers on the wire, so any language can consume it.
 * </ul>
 *
 * <p>A timeout does not prove the record was lost; it may still be written. The client then retries
 * and the transaction is duplicated, which is why idempotency keys (Feature 002) and consumer-side
 * dedupe (Feature 003) exist.
 */
public class KafkaTransactionPublisher implements TransactionPublisher {

    private final KafkaTemplate<String, String> template;
    private final JsonMapper mapper;
    private final String topic;
    private final Duration timeout;
    private final Counter ingested;

    public KafkaTransactionPublisher(
            KafkaTemplate<String, String> template, JsonMapper mapper, String topic, Duration timeout, MeterRegistry meters) {
        this.template = template;
        this.mapper = mapper;
        this.topic = topic;
        this.timeout = timeout;
        this.ingested = Counter.builder("transactions_ingested_total")
                .description("Transactions durably accepted (broker-acknowledged)")
                .register(meters);
    }

    @Override
    public void publish(ReceivedTransaction received) {
        TransactionReceivedEvent event = toEvent(received);
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, event.accountId(), mapper.writeValueAsString(event));
        record.headers()
                .add(EventHeaders.EVENT_TYPE, bytes(TransactionReceivedEvent.EVENT_TYPE))
                .add(EventHeaders.SCHEMA_VERSION, bytes(String.valueOf(TransactionReceivedEvent.SCHEMA_VERSION)));
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            record.headers().add(EventHeaders.REQUEST_ID, bytes(requestId)); // correlation across services
        }
        // traceparent is added by the KafkaTemplate's observation (spring.kafka.template.observation-enabled)

        try {
            template.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            ingested.increment();
        } catch (TimeoutException e) {
            throw new EventPublishingException("Kafka did not acknowledge within " + timeout, e);
        } catch (ExecutionException e) {
            throw new EventPublishingException("Kafka rejected the record", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Interrupted while waiting for Kafka acknowledgement", e);
        }
    }

    private static TransactionReceivedEvent toEvent(ReceivedTransaction received) {
        Transaction tx = received.transaction();
        return new TransactionReceivedEvent(
                TransactionReceivedEvent.SCHEMA_VERSION,
                received.eventId(),
                tx.transactionId(),
                tx.accountId(),
                tx.amount().amount(),
                tx.amount().currency().getCurrencyCode(),
                tx.merchantId(),
                tx.merchantCategoryCode(),
                tx.country(),
                tx.channel().name(),
                tx.occurredAt(),
                received.receivedAt());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
