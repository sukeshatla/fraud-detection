package com.fraudplatform.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Contract guard for {@code fraud.alerts.v1}; see TransactionReceivedEventContractTest for the rationale. */
class FraudAlertEventContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    @DisplayName("AC-003-10: v1 golden alert payload deserialises with every field intact")
    void goldenPayloadDeserialises() throws IOException {
        FraudAlertEvent event = mapper.readValue(golden(), FraudAlertEvent.class);

        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.alertEventId()).isEqualTo(UUID.fromString("7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f"));
        assertThat(event.sourceEventId()).isEqualTo(UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11"));
        assertThat(event.transactionId()).isEqualTo("txn-7f3a9c");
        assertThat(event.accountId()).isEqualTo("acc-1001");
        assertThat(event.amount()).isEqualByComparingTo("7500.00");
        assertThat(event.merchantCategoryCode()).isEqualTo("7995");
        assertThat(event.ruleScore()).isEqualTo(85);
        assertThat(event.riskScore()).isEqualTo(85);
        assertThat(event.decision()).isEqualTo("DECLINE");
        assertThat(event.ruleHits()).extracting(FraudAlertEvent.RuleHit::code)
                .containsExactly("HIGH_AMOUNT", "GEO_VELOCITY", "HIGH_RISK_MCC");
        assertThat(event.scoredAt()).isEqualTo(Instant.parse("2026-10-09T18:15:30.450Z"));
    }

    @Test
    @DisplayName("Serialise → deserialise round-trip is lossless")
    void roundTrip() {
        FraudAlertEvent original = new FraudAlertEvent(
                FraudAlertEvent.SCHEMA_VERSION, UUID.randomUUID(), UUID.randomUUID(), "txn-1", "acc-1",
                new BigDecimal("1.50"), "USD", "m-1", "5411", "US", "CARD_NOT_PRESENT",
                Instant.parse("2026-01-01T00:00:00Z"), 45, 45, "REVIEW",
                List.of(new FraudAlertEvent.RuleHit("CARD_TESTING", 45, "3 small txns in 5m")),
                Instant.parse("2026-01-01T00:00:01Z"));

        FraudAlertEvent copy = mapper.readValue(mapper.writeValueAsString(original), FraudAlertEvent.class);

        assertThat(copy).isEqualTo(original);
    }

    private InputStream golden() {
        return getClass().getResourceAsStream("/contracts/fraud-alert-v1.json");
    }
}
