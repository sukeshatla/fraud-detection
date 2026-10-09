package com.fraudplatform.alerts.application;

import java.util.Optional;
import java.util.UUID;

/** Outbound port: short-lived, cross-instance exclusive lock per alert (layer 1). */
public interface AlertLock {

    /** @return a handle if acquired; empty if someone else holds it (never blocks). */
    Optional<Handle> tryLock(UUID alertId);

    @FunctionalInterface
    interface Handle extends AutoCloseable {
        @Override
        void close(); // releases; no checked exception
    }
}
