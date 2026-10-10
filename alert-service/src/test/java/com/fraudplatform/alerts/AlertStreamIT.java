package com.fraudplatform.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.testing.TestJwts;
import com.fraudplatform.alerts.support.IntegrationTest;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/** AC-008-02: a browser connected over SSE sees new alerts and status changes as they happen. */
@IntegrationTest
class AlertStreamIT {

    @Autowired
    private Environment env;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("AC-008-02: SSE delivers alert.created for a Kafka-ingested alert and alert.updated for a review")
    void streamsCreatedAndUpdated() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + env.getProperty("local.server.port")
                + "/api/v1/alerts/stream?access_token=" + TestJwts.user("analyst-1", "ANALYST")))
                .header("Accept", "text/event-stream").build();
        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/event-stream"));
        BufferedReader lines = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));

        FraudAlertEvent event = alert();
        kafkaTemplate.send(Topics.FRAUD_ALERTS, event.accountId(), mapper.writeValueAsString(event)).get();
        assertThat(nextEvent(lines, "alert.created", event.transactionId())).contains(event.alertEventId().toString());

        assertThat(mvc.patch().uri("/api/v1/alerts/" + event.alertEventId())
                .header("Authorization", TestJwts.bearer(TestJwts.user("analyst-1", "ANALYST"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\",\"version\":0}").exchange()).hasStatus(HttpStatus.OK);
        assertThat(nextEvent(lines, "alert.updated", event.transactionId())).contains("UNDER_REVIEW");
        response.body().close();
    }

    @Test
    @DisplayName("AC-015-02: the query-string token is accepted ONLY on the stream; no token → 401")
    void queryTokenOnlyOnStream() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://localhost:" + env.getProperty("local.server.port");
        String token = TestJwts.user("analyst-1", "ANALYST");

        assertThat(client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/alerts/stream")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/alerts?access_token=" + token)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
    }

    /** Reads SSE lines until an event named {@code name} whose data mentions {@code marker}; returns its data. */
    private static String nextEvent(BufferedReader lines, String name, String marker) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String currentEvent = null;
                for (String line; (line = lines.readLine()) != null; ) {
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:") && name.equals(currentEvent) && line.contains(marker)) {
                        return line.substring(5);
                    }
                }
                throw new IllegalStateException("stream closed");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }).get(30, TimeUnit.SECONDS);
    }

    private static FraudAlertEvent alert() {
        Instant now = Instant.now();
        return new FraudAlertEvent(1, UUID.randomUUID(), UUID.randomUUID(), "txn-" + UUID.randomUUID(), "acc-sse",
                new BigDecimal("9000.00"), "USD", "m-1", "7995", "MT", "CARD_NOT_PRESENT", now, 85, 90, "DECLINE",
                List.of(new FraudAlertEvent.RuleHit("HIGH_AMOUNT", 30, "big")), now, 0.91, "lr-v1");
    }

}
