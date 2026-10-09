package com.fraudplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end through the real HTTP stack into a real Kafka broker (ADR-0005: no embedded fakes).
 * Requires Docker. Run with {@code ./mvnw verify}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionIngestionIT {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.1.0");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper mapper;

    @Test
    @DisplayName("AC-001-01/02: accepted transaction is on transactions.received.v1, keyed by account, with headers")
    void publishesAcceptedTransactionToKafka() {
        String accountId = uniqueAccount();

        MvcTestResult result = submit(accountId, "txn-" + UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String eventId = mapper.readTree(result.getResponse().getContentAsByteArray()).get("eventId").asString();

        ConsumerRecord<String, String> record = consume(accountId, 1).getFirst();
        TransactionReceivedEvent event = mapper.readValue(record.value(), TransactionReceivedEvent.class);

        assertThat(record.key()).isEqualTo(accountId);
        assertThat(header(record, EventHeaders.EVENT_TYPE)).isEqualTo("TransactionReceived");
        assertThat(header(record, EventHeaders.SCHEMA_VERSION)).isEqualTo("1");
        assertThat(event.eventId()).hasToString(eventId);
        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.amount()).isEqualByComparingTo("249.99");
    }

    @Test
    @DisplayName("AC-001-08: all transactions of one account land on one partition, in submission order")
    void sameAccountSamePartitionInOrder() {
        String accountId = uniqueAccount();
        List<String> submitted = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String txnId = "txn-" + i + "-" + UUID.randomUUID();
            submitted.add(txnId);
            assertThat(submit(accountId, txnId)).hasStatus(HttpStatus.ACCEPTED);
        }

        List<ConsumerRecord<String, String>> records = consume(accountId, submitted.size());

        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records)
                .extracting(r -> mapper.readValue(r.value(), TransactionReceivedEvent.class).transactionId())
                .containsExactlyElementsOf(submitted);
    }

    private MvcTestResult submit(String accountId, String transactionId) {
        String body = """
                {
                  "transactionId": "%s",
                  "accountId": "%s",
                  "amount": 249.99,
                  "currency": "USD",
                  "merchantId": "m-5541",
                  "merchantCategoryCode": "5732",
                  "country": "US",
                  "channel": "CARD_NOT_PRESENT",
                  "occurredAt": "%s"
                }
                """.formatted(transactionId, accountId, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return mvc.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    /** Reads the topic from the beginning until {@code expected} records with this key have arrived. */
    private static List<ConsumerRecord<String, String>> consume(String key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(Topics.TRANSACTIONS_RECEIVED));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(250)).forEach(r -> {
                    if (key.equals(r.key())) {
                        matching.add(r);
                    }
                });
                return matching.size() >= expected;
            });
        }
        return matching;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static String uniqueAccount() {
        return "acc-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
