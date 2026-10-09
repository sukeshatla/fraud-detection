package com.fraudplatform.scoring.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.application.AssessmentRepository;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.support.IntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the composite index serves the account-history query with no sort step (AC-004-04).
 *
 * <p>Lesson learned while writing this test: for an account with only ~25 rows, PostgreSQL rightly
 * prefers a bitmap scan + in-memory sort of 25 rows. The index-ordered plan wins for what matters:
 * a <b>busy</b> account with thousands of rows and a small LIMIT, where it reads 20 index entries in
 * order and stops. Up-to-date statistics ({@code ANALYZE}) on <i>both</i> joined tables matter too.
 */
@IntegrationTest
class QueryPlanIT {

    @Autowired
    private AssessmentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-004-04: last-50-for-account uses Index Scan on (account_id, occurred_at DESC) and no Sort node")
    void historyQueryUsesCompositeIndex() {
        seed("plan-acc-", 300, 20);    // background: many small accounts
        seed("plan-busy-", 1, 3_000);  // one busy merchant-facing account
        jdbc.execute("ANALYZE transaction");
        jdbc.execute("ANALYZE risk_score");

        // HISTORY_SQL has two placeholders: account_id = ? and LIMIT ?
        String explain = "EXPLAIN (FORMAT JSON) " + JdbcAssessmentRepository.HISTORY_SQL
                .replaceFirst("\\?", "'plan-busy-0'")
                .replaceFirst("\\?", "20");
        String plan = String.join("\n", jdbc.queryForList(explain, String.class));

        assertThat(plan).contains("ix_transaction_account_occurred");
        assertThat(plan).containsAnyOf("\"Node Type\": \"Index Scan\"", "\"Node Type\": \"Index Only Scan\"");
        assertThat(plan).doesNotContain("\"Node Type\": \"Sort\"");
    }

    private void seed(String prefix, int accounts, int perAccount) {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        List<RiskAssessment> batch = new ArrayList<>();
        for (int a = 0; a < accounts; a++) {
            for (int i = 0; i < perAccount; i++) {
                batch.add(JdbcAssessmentRepositoryIT.assessment(prefix + a, prefix + a + "-" + i,
                        t0.plusSeconds(a * 1000L + i), 0, List.of()));
            }
        }
        repository.saveAll(batch, List.of());
    }
}
