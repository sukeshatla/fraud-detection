package com.fraudplatform.alerts.infrastructure.persistence;

import static com.fraudplatform.alerts.application.AlertFixtures.newAlert;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.alerts.application.AlertView;
import com.fraudplatform.alerts.application.Cursor;
import com.fraudplatform.alerts.application.KeysetPage;
import com.fraudplatform.alerts.application.KeysetQuery;
import com.fraudplatform.alerts.application.OffsetPage;
import com.fraudplatform.alerts.application.PageQuery;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import com.fraudplatform.alerts.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AlertQueryIT {

    @Autowired
    private AlertRepository repository;

    @Autowired
    private AlertJpaRepository jpa;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private TransactionTemplate tx;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        seed(60, Instant.parse("2097-01-01T00:00:00Z")); // ≥ 50 OPEN/HIGH alerts with 2 rule hits each
    }

    @Test
    @DisplayName("AC-007-03: NAIVE lazy loading issues 1 + N statements for a page of N alerts")
    void naiveMappingIsNPlusOne() {
        stats.clear();

        int[] pageSizeAndHits = tx.execute(status -> {
            List<AlertEntity> page = jpa.findByStatusAndSeverityOrderByCreatedAtDescIdDesc(
                    AlertStatus.OPEN, Severity.HIGH, PageRequest.of(0, 50));
            int hits = page.stream().mapToInt(a -> a.getRuleHits().size()).sum(); // each touch = one more SELECT
            return new int[] {page.size(), hits};
        });

        assertThat(pageSizeAndHits).containsExactly(50, 100);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1 + 50);
    }

    @Test
    @DisplayName("AC-007-03: our adapter loads 50 alerts + their hits + total in ≤ 3 statements")
    void adapterAvoidsNPlusOne() {
        stats.clear();

        OffsetPage page = repository.findPage(new PageQuery(AlertStatus.OPEN, Optional.of(Severity.HIGH), 0, 50));

        assertThat(page.items()).hasSize(50).allSatisfy(a -> assertThat(a.ruleHits()).hasSize(2));
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(60);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("AC-007-02: keyset paging walks the queue newest-first with no gaps or duplicates")
    void keysetPagination() {
        List<UUID> seeded = seed(20, Instant.parse("2099-06-01T00:00:00Z")); // newest in the table → top of the queue
        List<UUID> walked = new ArrayList<>();
        Optional<Cursor> cursor = Optional.empty();
        while (walked.size() < 20) {
            KeysetPage page = repository.findAfter(new KeysetQuery(AlertStatus.OPEN, Optional.of(Severity.HIGH), cursor, 7));
            page.items().stream().map(AlertView::id).forEach(walked::add);
            cursor = page.next();
        }

        assertThat(walked.subList(0, 20)).containsExactlyElementsOf(seeded.reversed());
    }

    @Test
    @DisplayName("AC-007-01: inserting the same alert twice is a no-op")
    void insertIsIdempotent() {
        var alert = newAlert(UUID.randomUUID(), "txn-" + UUID.randomUUID(), "acc-1", Instant.now());

        assertThat(repository.insertIfAbsent(alert)).isTrue();
        assertThat(repository.insertIfAbsent(alert)).isFalse();
    }

    @Test
    @DisplayName("AC-007-10: details carry hits and the (initially empty) audit trail")
    void details() {
        UUID id = UUID.randomUUID();
        repository.insertIfAbsent(newAlert(id, "txn-" + id, "acc-1", Instant.now()));

        var details = repository.findDetails(id).orElseThrow();

        assertThat(details.alert().ruleHits()).extracting("code").containsExactly("HIGH_AMOUNT", "HIGH_RISK_MCC");
        assertThat(details.history()).isEmpty();
    }

    /** Inserts {@code n} alerts at {@code base + i seconds}; returns their ids in insertion (oldest-first) order. */
    private List<UUID> seed(int n, Instant base) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            repository.insertIfAbsent(newAlert(id, "txn-" + id, "acc-" + i, base.plusSeconds(i)));
            ids.add(id);
        }
        return ids;
    }
}
