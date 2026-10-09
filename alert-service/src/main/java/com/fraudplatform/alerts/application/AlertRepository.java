package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: alert persistence. */
public interface AlertRepository {

    /** Idempotent: returns false if an alert with this id or transactionId already exists. */
    boolean insertIfAbsent(NewAlert alert);

    Optional<AlertView> findById(UUID id);

    Optional<AlertDetails> findDetails(UUID id);

    OffsetPage findPage(PageQuery query);

    KeysetPage findAfter(KeysetQuery query);

    /**
     * Moves the alert to {@code to} and writes an audit row, atomically, only if it is still at
     * {@code expectedVersion}. Throws {@link StaleAlertException} otherwise (optimistic locking).
     */
    AlertView transition(UUID id, long expectedVersion, AlertStatus to, String actor, Instant at);
}
