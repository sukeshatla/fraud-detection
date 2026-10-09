package com.fraudplatform.alerts.infrastructure.kafka;

import com.fraudplatform.alerts.application.AlertResolution;
import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.AlertResolvedEvent;
import com.fraudplatform.messaging.outbox.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Maps a resolution to the {@code fraud.alert-resolutions.v1} contract as an outbox message. */
public class AlertResolvedEvents {

    private final JsonMapper mapper;

    public AlertResolvedEvents(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public OutboxMessage toOutbox(AlertResolution r) {
        AlertResolvedEvent event = new AlertResolvedEvent(AlertResolvedEvent.SCHEMA_VERSION,
                // deterministic: one id per alert + resolution, so consumers can dedupe re-sends
                UUID.nameUUIDFromBytes(("resolved:" + r.alertId() + ":" + r.resolution()).getBytes(StandardCharsets.UTF_8)),
                r.alertId(), r.transactionId(), r.accountId(), r.resolution().name(), r.resolvedBy(), r.resolvedAt());
        return new OutboxMessage(Topics.ALERT_RESOLUTIONS, r.accountId(), mapper.writeValueAsString(event), Map.of(
                EventHeaders.EVENT_TYPE, AlertResolvedEvent.EVENT_TYPE,
                EventHeaders.SCHEMA_VERSION, String.valueOf(AlertResolvedEvent.SCHEMA_VERSION)));
    }
}
