package com.fraudplatform.ingestion.infrastructure.kafka;

import static com.fraudplatform.ingestion.domain.TransactionFixtures.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.ingestion.application.EventPublishingException;
import com.fraudplatform.ingestion.domain.ReceivedTransaction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
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
class KafkaTransactionPublisherTest {

    private static final String TOPIC = "transactions.received.v1";
    private static final ReceivedTransaction RECEIVED = new ReceivedTransaction(
            UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11"),
            Instant.parse("2026-10-09T18:15:30.120Z"),
            aTransaction());

    @Mock
    private KafkaTemplate<String, String> template;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private KafkaTransactionPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaTransactionPublisher(template, mapper, TOPIC, Duration.ofMillis(100));
    }

    @Test
    @DisplayName("AC-001-02: publishes to the topic keyed by accountId with type + version headers")
    void publishesKeyedRecordWithHeaders() {
        given(template.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publish(RECEIVED);

        ProducerRecord<String, String> record = captureRecord();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo("acc-1001");
        assertThat(header(record, EventHeaders.EVENT_TYPE)).isEqualTo("TransactionReceived");
        assertThat(header(record, EventHeaders.SCHEMA_VERSION)).isEqualTo("1");
    }

    @Test
    @DisplayName("AC-001-02: payload is the v1 TransactionReceivedEvent contract")
    void payloadMatchesContract() {
        given(template.send(any(ProducerRecord.class))).willReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publish(RECEIVED);

        TransactionReceivedEvent event = mapper.readValue(captureRecord().value(), TransactionReceivedEvent.class);
        assertThat(event).isEqualTo(new TransactionReceivedEvent(
                1,
                RECEIVED.eventId(),
                "txn-7f3a9c",
                "acc-1001",
                RECEIVED.transaction().amount().amount(),
                "USD",
                "m-5541",
                "5732",
                "US",
                "CARD_NOT_PRESENT",
                RECEIVED.transaction().occurredAt(),
                RECEIVED.receivedAt()));
    }

    @Test
    @DisplayName("AC-001-06: no broker ack within the timeout → EventPublishingException")
    void timesOutWhenBrokerDoesNotAcknowledge() {
        given(template.send(any(ProducerRecord.class))).willReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> publisher.publish(RECEIVED))
                .isInstanceOf(EventPublishingException.class)
                .hasMessageContaining("acknowledge");
    }

    @Test
    @DisplayName("AC-001-06: broker failure → EventPublishingException carrying the cause")
    void wrapsBrokerFailure() {
        given(template.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("NotEnoughReplicas")));

        assertThatThrownBy(() -> publisher.publish(RECEIVED))
                .isInstanceOf(EventPublishingException.class)
                .rootCause().hasMessage("NotEnoughReplicas");
    }

    @Test
    @DisplayName("Interrupt while waiting → interrupt flag restored, EventPublishingException thrown")
    void restoresInterruptFlag() {
        given(template.send(any(ProducerRecord.class))).willReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> publisher.publish(RECEIVED)).isInstanceOf(EventPublishingException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear so later tests are unaffected
        }
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> captureRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static SendResult<String, String> sendResult() {
        return new SendResult<>(null, null);
    }
}
