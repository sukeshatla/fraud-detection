package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Use case: an analyst moves an alert through the workflow.
 *
 * <p><b>Two-layer concurrent-write protection.</b>
 * <ol>
 *   <li>Layer 1, Redis lock: rejects a concurrent request for the same alert cheaply, before any
 *       DB work. An <i>optimisation</i>: a lease can expire mid-request (GC pause, partition).
 *   <li>Layer 2, optimistic locking: the update only applies if the row is still at the version
 *       the client saw ({@code UPDATE … WHERE version = ?}). The <i>guarantee</i>: it holds even
 *       if layer 1 failed.
 * </ol>
 */
public class ReviewAlertService {

    private final AlertRepository repository;
    private final AlertLock lock;
    private final AlertChangeBus changes;
    private final Clock clock;

    public ReviewAlertService(AlertRepository repository, AlertLock lock, AlertChangeBus changes, Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.changes = changes;
        this.clock = clock;
    }

    public AlertView review(UUID id, AlertStatus to, long expectedVersion, String actor) {
        AlertView updated;
        try (AlertLock.Handle ignored = lock.tryLock(id).orElseThrow(() -> new AlertLockedException(id))) {
            AlertView current = repository.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
            if (current.version() != expectedVersion) {
                throw new StaleAlertException(current);
            }
            current.status().requireTransitionTo(to);
            Instant now = clock.instant();
            Optional<AlertResolution> resolution = to.isTerminal()
                    ? Optional.of(new AlertResolution(id, current.transactionId(), current.accountId(), to, actor, now))
                    : Optional.empty();
            // Status change + audit row + resolution event: one transaction (outbox, Feature 010)
            updated = repository.transition(id, expectedVersion, to, actor, now, resolution);
        }
        // Committed: refresh live dashboards (best effort; the resolution event is already in the outbox).
        changes.publish(new AlertChange(AlertChange.Type.UPDATED, updated));
        return updated;
    }
}
