package com.fraudplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.testing.KafkaTestConsumer;
import com.fraudplatform.ingestion.support.IntegrationTest;
import com.fraudplatform.ingestion.support.TransactionJson;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end through the real HTTP stack into a real Kafka broker (ADR-0005: no embedded fakes). */
@IntegrationTest
class TransactionIngestionIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("AC-001-01/02: accepted transaction is on transactions.received.v1, keyed by account, with headers")
    void publishesAcceptedTransactionToKafka() {
        String accountId = uniqueAccount();

        MvcTestResult result = submit(accountId, "txn-" + UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String eventId = mapper.readTree(result.getResponse().getContentAsByteArray()).get("eventId").asString();

        ConsumerRecord<String, String> record = KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.TRANSACTIONS_RECEIVED, accountId, 1).getFirst();
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

        List<ConsumerRecord<String, String>> records = KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.TRANSACTIONS_RECEIVED, accountId, submitted.size());

        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records)
                .extracting(r -> mapper.readValue(r.value(), TransactionReceivedEvent.class).transactionId())
                .containsExactlyElementsOf(submitted);
    }

    private MvcTestResult submit(String accountId, String transactionId) {
        return mvc.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TransactionJson.valid(transactionId, accountId))
                .exchange();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static String uniqueAccount() {
        return "acc-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
