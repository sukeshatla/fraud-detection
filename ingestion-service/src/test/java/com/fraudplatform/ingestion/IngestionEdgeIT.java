package com.fraudplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.ingestion.support.KafkaTestConsumer;
import com.fraudplatform.ingestion.support.TestcontainersConfiguration;
import com.fraudplatform.ingestion.support.TransactionJson;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/** Feature 002 end-to-end: HTTP → Redis (limits, idempotency) → Kafka. */
@SpringBootTest(properties = {
        "fraud.ingestion.rate-limit.clients.it-limited.capacity=2",
        "fraud.ingestion.rate-limit.clients.it-limited.refill-per-second=0.001"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IngestionEdgeIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("AC-002-05: retried POST with the same Idempotency-Key returns the original eventId and publishes once")
    void duplicatePostIsPublishedOnce() {
        String accountId = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        String body = TransactionJson.valid("txn-" + UUID.randomUUID(), accountId);
        String key = UUID.randomUUID().toString();

        MvcTestResult first = post(body, key, "gw-1");
        MvcTestResult retry = post(body, key, "gw-1");

        assertThat(first).hasStatus(HttpStatus.ACCEPTED);
        assertThat(retry).hasStatus(HttpStatus.ACCEPTED);
        assertThat(retry).hasHeader("Idempotent-Replayed", "true");
        assertThat(eventId(retry)).isEqualTo(eventId(first));
        assertThat(KafkaTestConsumer.recordsWithin(
                kafka.getBootstrapServers(), Topics.TRANSACTIONS_RECEIVED, accountId, Duration.ofSeconds(3)))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-002-06: same Idempotency-Key with a different body → 422")
    void keyReuseWithDifferentBodyIsRejected() {
        String key = UUID.randomUUID().toString();

        assertThat(post(TransactionJson.valid("txn-a", "acc-a"), key, "gw-1")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(post(TransactionJson.valid("txn-b", "acc-b"), key, "gw-1")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("AC-002-01: client over its quota gets 429 from the Redis-backed limiter")
    void clientOverQuotaIsThrottled() {
        String body = TransactionJson.valid("txn-" + UUID.randomUUID(), "acc-rl");

        assertThat(post(body, null, "it-limited")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(post(body, null, "it-limited")).hasStatus(HttpStatus.ACCEPTED);
        MvcTestResult third = post(body, null, "it-limited");

        assertThat(third).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third).hasHeader("RateLimit-Limit", "2");
    }

    private MvcTestResult post(String body, String idempotencyKey, String clientId) {
        var request = mvc.post().uri("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Client-Id", clientId)
                .content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return request.exchange();
    }

    private String eventId(MvcTestResult result) {
        return mapper.readTree(result.getResponse().getContentAsByteArray()).get("eventId").asString();
    }
}
