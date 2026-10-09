package com.fraudplatform.messaging.outbox;

import java.util.Map;

/** A Kafka record to publish once the surrounding database transaction commits. */
public record OutboxMessage(String topic, String key, String payload, Map<String, String> headers) {

    public OutboxMessage {
        headers = Map.copyOf(headers);
    }
}
