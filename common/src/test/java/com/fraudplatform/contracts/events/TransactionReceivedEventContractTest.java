package com.fraudplatform.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumer-driven contract guard for {@code transactions.received.v1}.
 *
 * <p>The golden file represents a message already sitting in Kafka. If a change to the record
 * breaks deserialisation of that file, it breaks every consumer reading existing data, and the
 * change must go to a new topic version instead.
 */
class TransactionReceivedEventContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    @DisplayName("AC-001-02: v1 golden payload deserialises with every field intact")
    void goldenPayloadDeserialises() throws IOException {
        TransactionReceivedEvent event = mapper.readValue(golden(), TransactionReceivedEvent.class);

        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.eventId()).isEqualTo(UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11"));
        assertThat(event.transactionId()).isEqualTo("txn-7f3a9c");
        assertThat(event.accountId()).isEqualTo("acc-1001");
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("249.99"));
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.merchantId()).isEqualTo("m-5541");
        assertThat(event.merchantCategoryCode()).isEqualTo("5732");
        assertThat(event.country()).isEqualTo("US");
        assertThat(event.channel()).isEqualTo("CARD_NOT_PRESENT");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-09T18:15:30Z"));
        assertThat(event.receivedAt()).isEqualTo(Instant.parse("2026-10-09T18:15:30.120Z"));
    }

    @Test
    @DisplayName("Serialise → deserialise round-trip is lossless")
    void roundTrip() {
        TransactionReceivedEvent original = new TransactionReceivedEvent(
                TransactionReceivedEvent.SCHEMA_VERSION,
                UUID.randomUUID(),
                "txn-1",
                "acc-1",
                new BigDecimal("10.05"),
                "EUR",
                "m-1",
                "5411",
                "DE",
                "CONTACTLESS",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"));

        String json = mapper.writeValueAsString(original);
        TransactionReceivedEvent copy = mapper.readValue(json, TransactionReceivedEvent.class);

        assertThat(copy).isEqualTo(original);
    }

    @Test
    @DisplayName("Forward compatibility: unknown fields added by a newer producer are ignored")
    void unknownFieldsIgnored() throws IOException {
        String withExtraField = new String(golden().readAllBytes())
                .replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"deviceFingerprint\": \"abc\",");

        TransactionReceivedEvent event = mapper.readValue(withExtraField, TransactionReceivedEvent.class);

        assertThat(event.transactionId()).isEqualTo("txn-7f3a9c");
    }

    private InputStream golden() {
        return getClass().getResourceAsStream("/contracts/transaction-received-v1.json");
    }
}
