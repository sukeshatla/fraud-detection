package com.fraudplatform.alerts.application;

import java.util.function.Consumer;

/**
 * Outbound port: broadcasts alert changes to <b>every</b> service instance (not just one, as a
 * Kafka consumer group would), so each instance can push them to its own SSE clients.
 * Best effort: the database stays the source of truth and clients refetch on reconnect.
 */
public interface AlertChangeBus {

    void publish(AlertChange change);

    /** @return call to unsubscribe */
    Runnable subscribe(Consumer<AlertChange> subscriber);
}
