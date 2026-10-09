package com.fraudplatform.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class AlertResolvedEventContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    @DisplayName("AC-007-09: v1 golden resolution payload deserialises with every field intact")
    void goldenPayloadDeserialises() throws IOException {
        AlertResolvedEvent event;
        try (var in = getClass().getResourceAsStream("/contracts/alert-resolved-v1.json")) {
            event = mapper.readValue(in, AlertResolvedEvent.class);
        }

        assertThat(event).isEqualTo(new AlertResolvedEvent(1,
                UUID.fromString("3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
                UUID.fromString("7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f"),
                "txn-7f3a9c", "acc-1001", "FALSE_POSITIVE", "analyst-17",
                Instant.parse("2026-10-09T19:02:11.500Z")));
    }
}
