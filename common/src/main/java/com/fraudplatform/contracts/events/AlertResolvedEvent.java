package com.fraudplatform.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@link com.fraudplatform.contracts.Topics#ALERT_RESOLUTIONS} when an analyst closes
 * an alert. Record key: {@code accountId}. scoring-service clears the account's high-risk flag on
 * {@code FALSE_POSITIVE}.
 *
 * @param resolution CONFIRMED_FRAUD | FALSE_POSITIVE
 */
public record AlertResolvedEvent(
        int schemaVersion,
        UUID eventId,
        UUID alertId,
        String transactionId,
        String accountId,
        String resolution,
        String resolvedBy,
        Instant resolvedAt) {

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE = "AlertResolved";
}
