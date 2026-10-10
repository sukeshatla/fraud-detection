package com.fraudplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.ingestion.support.IntegrationTest;
import com.fraudplatform.ingestion.support.TransactionJson;
import com.fraudplatform.testing.KafkaTestConsumer;
import com.fraudplatform.testing.TestJwts;
import java.nio.charset.StandardCharsets;
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

/** Feature 012: metrics, correlation ids and trace context, end to end. */
@IntegrationTest
class ObservabilityIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("AC-012-04/05: X-Request-Id and W3C traceparent travel from HTTP into the Kafka record")
    void propagatesCorrelationAndTraceContext() {
        String account = "acc-" + UUID.randomUUID().toString().substring(0, 8);

        MvcTestResult result = mvc.post().uri("/api/v1/transactions").header("X-Request-Id", "rid-obs-1")
                .header("Authorization", TestJwts.bearer(TestJwts.client("gw-it", "INGEST")))
                .contentType(MediaType.APPLICATION_JSON).content(TransactionJson.valid("txn-" + UUID.randomUUID(), account))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.ACCEPTED).hasHeader("X-Request-Id", "rid-obs-1");
        ConsumerRecord<String, String> record = KafkaTestConsumer
                .awaitRecords(kafka.getBootstrapServers(), Topics.TRANSACTIONS_RECEIVED, account, 1).getFirst();
        assertThat(header(record, EventHeaders.REQUEST_ID)).isEqualTo("rid-obs-1");
        // W3C: version-traceId-spanId-flags
        assertThat(header(record, EventHeaders.TRACEPARENT)).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    }

    @Test
    @DisplayName("AC-012-01/02: /actuator/prometheus exposes RED histograms and the business counters")
    void prometheusScrape() throws Exception {
        mvc.post().uri("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", TestJwts.bearer(TestJwts.client("gw-it", "INGEST")))
                .content(TransactionJson.valid("txn-" + UUID.randomUUID(), "acc-metrics")).exchange();

        MvcTestResult scrape = mvc.get().uri("/actuator/prometheus").exchange();

        assertThat(scrape).hasStatus(HttpStatus.OK);
        String body = scrape.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("http_server_requests_seconds_bucket")          // latency histogram (SLO buckets)
                .contains("transactions_ingested_total")
                .contains("kafka_producer_");                              // Kafka client metrics
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
