package com.fraudplatform.alerts.infrastructure.persistence;

import static com.fraudplatform.alerts.application.AlertFixtures.newAlert;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.alerts.application.AlertStats;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import com.fraudplatform.alerts.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AlertStatsIT {

    @Autowired
    private AlertRepository repository;

    @Test
    @DisplayName("AC-008-06: stats move when alerts are raised and resolved")
    void statsReflectChanges() {
        Instant since = Instant.now().minusSeconds(3600);
        AlertStats before = repository.stats(since);

        UUID dismissed = UUID.randomUUID();
        repository.insertIfAbsent(newAlert(UUID.randomUUID(), "txn-" + UUID.randomUUID(), "acc", Instant.now()));
        repository.insertIfAbsent(newAlert(dismissed, "txn-" + dismissed, "acc", Instant.now()));
        repository.transition(dismissed, 0, AlertStatus.UNDER_REVIEW, "a", Instant.now());
        repository.transition(dismissed, 1, AlertStatus.FALSE_POSITIVE, "a", Instant.now());

        AlertStats after = repository.stats(since);
        assertThat(after.openBySeverity().get(Severity.HIGH)).isEqualTo(before.openBySeverity().get(Severity.HIGH) + 1);
        assertThat(after.raised()).isEqualTo(before.raised() + 2);
        assertThat(after.falsePositives()).isEqualTo(before.falsePositives() + 1);
    }
}
