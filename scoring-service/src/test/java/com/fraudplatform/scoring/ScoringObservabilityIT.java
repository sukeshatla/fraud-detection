package com.fraudplatform.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.scoring.support.IntegrationTest;
import com.fraudplatform.testing.KafkaTestConsumer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** Feature 012 in scoring: trace context survives batch scoring AND the outbox; metrics are scraped. */
@IntegrationTest
class ScoringObservabilityIT {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Autowired
    private org.springframework.kafka.core.ProducerFactory<String, String> producers;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("AC-012-04/05: the alert carries the SAME trace id and request id as the transaction that caused it")
    void traceContextCrossesTheOutbox() throws Exception {
        String account = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        var event = new TransactionReceivedEvent(1, UUID.randomUUID(), "txn-" + UUID.randomUUID(), account,
                new BigDecimal("9000.00"), "USD", "m-1", "7995", "US", "CARD_NOT_PRESENT", now, now);
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.TRANSACTIONS_RECEIVED, account, mapper.writeValueAsString(event));
        record.headers().add(EventHeaders.TRACEPARENT, TRACEPARENT.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.REQUEST_ID, "rid-scoring-1".getBytes(StandardCharsets.UTF_8));
        // A plain (non-observed) template: an observed one would append its OWN traceparent,
        // the exact effect the outbox relay avoids by using a non-observed template too.
        new KafkaTemplate<>(producers).send(record).get();

        ConsumerRecord<String, String> alert = KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.FRAUD_ALERTS, account, 1).getFirst();

        assertThat(header(alert, EventHeaders.REQUEST_ID)).isEqualTo("rid-scoring-1");
        assertThat(traceId(header(alert, EventHeaders.TRACEPARENT))).isEqualTo(traceId(TRACEPARENT));
    }

    @Test
    @DisplayName("AC-012-02: scoring, ML, cache, outbox and Kafka-consumer metrics are exposed for Prometheus")
    void prometheusScrape() throws Exception {
        String body = mvc.get().uri("/actuator/prometheus").exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).hasStatus(HttpStatus.OK);
        assertThat(body).contains("transactions_scored_total", "fraud_risk_score", "fraud_detection_latency_seconds",
                "scoring_batch_size", "outbox_relayed_total", "ml_fallback_total", "cache_requests_total",
                "kafka_consumer_fetch_manager_records_lag");
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String traceId(String traceparent) {
        return traceparent == null ? null : traceparent.split("-")[1];
    }
}
