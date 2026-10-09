package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Use case: read the analyst queue. */
public class AlertQueryService {

    static final int MAX_PAGE_SIZE = 100;

    static final Duration STATS_WINDOW = Duration.ofHours(1);

    private final AlertRepository repository;
    private final Clock clock;

    public AlertQueryService(AlertRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public OffsetPage page(AlertStatus status, Optional<Severity> severity, int page, int size) {
        return repository.findPage(new PageQuery(status, severity, Math.max(0, page), clamp(size)));
    }

    public KeysetPage after(AlertStatus status, Optional<Severity> severity, Optional<Cursor> after, int size) {
        return repository.findAfter(new KeysetQuery(status, severity, after, clamp(size)));
    }

    public AlertStats stats() {
        return repository.stats(clock.instant().minus(STATS_WINDOW));
    }

    public AlertDetails details(UUID id) {
        return repository.findDetails(id).orElseThrow(() -> new AlertNotFoundException(id));
    }

    private static int clamp(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
