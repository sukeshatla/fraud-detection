package com.fraudplatform.scoring.infrastructure.persistence;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.application.AssessmentRepository;
import com.fraudplatform.scoring.application.TransactionHistoryEntry;
import com.fraudplatform.scoring.application.TransactionHistoryQuery;
import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JdbcAssessmentRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-10-09T18:00:00Z");

    @Autowired
    private AssessmentRepository repository;

    @Autowired
    private TransactionHistoryQuery history;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-004-02: a batch writes transaction, risk_score and rule_hit rows")
    void persistsBatch() {
        String acc = uniqueAccount();
        RiskAssessment risky = assessment(acc, "t-1", T0, 50, List.of(new RuleHit("HIGH_AMOUNT", 30, "big"), new RuleHit("HIGH_RISK_MCC", 20, "mcc")));
        RiskAssessment clean = assessment(acc, "t-2", T0.plusSeconds(1), 0, List.of());

        repository.saveAll(List.of(risky, clean));

        assertThat(count("transaction", acc)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM risk_score r JOIN transaction t ON t.id = r.transaction_pk WHERE t.account_id = ?""",
                Integer.class, acc)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT h.rule_code FROM rule_hit h JOIN transaction t ON t.id = h.transaction_pk
                WHERE t.account_id = ? ORDER BY h.rule_code""", String.class, acc))
                .containsExactly("HIGH_AMOUNT", "HIGH_RISK_MCC");
    }

    @Test
    @DisplayName("AC-004-03: replaying the same batch is a no-op (ON CONFLICT DO NOTHING), not an error")
    void replayIsIdempotent() {
        String acc = uniqueAccount();
        List<RiskAssessment> batch = List.of(assessment(acc, "t-" + UUID.randomUUID(), T0, 50, List.of(new RuleHit("X", 50, "x"))));

        repository.saveAll(batch);
        repository.saveAll(batch);

        assertThat(count("transaction", acc)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM rule_hit h JOIN transaction t ON t.id = h.transaction_pk WHERE t.account_id = ?""",
                Integer.class, acc)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-004-07: history is newest-first, limited, with score + decision from one JOIN")
    void historyNewestFirst() {
        String acc = uniqueAccount();
        repository.saveAll(List.of(
                assessment(acc, "old", T0, 0, List.of()),
                assessment(acc, "mid", T0.plusSeconds(60), 45, List.of()),
                assessment(acc, "new", T0.plusSeconds(120), 80, List.of())));

        List<TransactionHistoryEntry> entries = history.recentForAccount(acc, 2);

        assertThat(entries).extracting(TransactionHistoryEntry::transactionId).containsExactly("new", "mid");
        assertThat(entries).extracting(TransactionHistoryEntry::decision).containsExactly("DECLINE", "REVIEW");
    }

    static RiskAssessment assessment(String acc, String txnId, Instant at, int score, List<RuleHit> hits) {
        Transaction tx = aTransaction().eventId(UUID.randomUUID()).accountId(acc).transactionId(txnId).occurredAt(at).build();
        return new RiskAssessment(tx, score, score, Decision.forScore(score), hits, at.plusMillis(300));
    }

    private int count(String table, String acc) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE account_id = ?", Integer.class, acc);
    }

    private static String uniqueAccount() {
        return "acc-" + UUID.randomUUID();
    }
}
