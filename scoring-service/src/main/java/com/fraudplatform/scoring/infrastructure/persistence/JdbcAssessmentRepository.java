package com.fraudplatform.scoring.infrastructure.persistence;

import com.fraudplatform.scoring.application.AssessmentRepository;
import com.fraudplatform.scoring.application.TransactionHistoryEntry;
import com.fraudplatform.scoring.application.TransactionHistoryQuery;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.infrastructure.kafka.FraudAlertEvents;
import com.fraudplatform.messaging.outbox.OutboxWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Plain-JDBC persistence for the scoring hot path.
 *
 * <p>{@link #saveAll} issues three {@code batchUpdate}s inside <b>one</b> transaction: one commit
 * (one WAL fsync) for the whole Kafka poll. With {@code reWriteBatchedInserts=true} the driver
 * rewrites each batch into multi-row {@code INSERT … VALUES (…),(…)} statements.
 *
 * <p>Alerts are appended to the <b>outbox</b> in the same transaction (Feature 010): the assessment
 * and its alert commit together or not at all, and the relay publishes the alert afterwards.
 *
 * <p>Every insert is {@code ON CONFLICT DO NOTHING}, and the transaction PK is a deterministic
 * UUIDv7, so a redelivered batch is a silent no-op instead of a constraint violation.
 */
public class JdbcAssessmentRepository implements AssessmentRepository, TransactionHistoryQuery {

    static final int BATCH_SIZE = 500;

    static final String INSERT_TRANSACTION = """
            INSERT INTO transaction (id, transaction_id, event_id, account_id, amount, currency, merchant_id,
                                     merchant_category_code, country, channel, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING""";

    static final String INSERT_SCORE = """
            INSERT INTO risk_score (transaction_pk, rule_score, ml_probability, model_version, risk_score, decision, scored_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING""";

    static final String INSERT_HIT = """
            INSERT INTO rule_hit (transaction_pk, rule_code, weight, reason)
            VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING""";

    /** One statement, no N+1: transaction + its score via the PK join; index-ordered, no sort. */
    static final String HISTORY_SQL = """
            SELECT t.transaction_id, t.amount, t.currency, t.merchant_id, t.merchant_category_code, t.country,
                   t.channel, t.occurred_at, r.risk_score, r.decision
            FROM transaction t
            JOIN risk_score r ON r.transaction_pk = t.id
            WHERE t.account_id = ?
            ORDER BY t.occurred_at DESC
            LIMIT ?""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OutboxWriter outbox;
    private final FraudAlertEvents alertEvents;

    public JdbcAssessmentRepository(JdbcTemplate jdbc, TransactionTemplate tx, OutboxWriter outbox, FraudAlertEvents alertEvents) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.outbox = outbox;
        this.alertEvents = alertEvents;
    }

    public static UUID primaryKey(Transaction t) {
        return UuidV7.fromTimeAndName(t.occurredAt(), t.transactionId());
    }

    @Override
    public void saveAll(List<RiskAssessment> assessments, List<RiskAssessment> alerts) {
        List<RuleHitRow> hits = new ArrayList<>();
        for (RiskAssessment a : assessments) {
            UUID pk = primaryKey(a.transaction());
            a.hits().forEach(h -> hits.add(new RuleHitRow(pk, h)));
        }

        tx.executeWithoutResult(status -> {
            jdbc.batchUpdate(INSERT_TRANSACTION, assessments, BATCH_SIZE, (ps, a) -> {
                Transaction t = a.transaction();
                ps.setObject(1, primaryKey(t));
                ps.setString(2, t.transactionId());
                ps.setObject(3, t.eventId());
                ps.setString(4, t.accountId());
                ps.setBigDecimal(5, t.amount());
                ps.setString(6, t.currency());
                ps.setString(7, t.merchantId());
                ps.setString(8, t.merchantCategoryCode());
                ps.setString(9, t.country());
                ps.setString(10, t.channel());
                ps.setTimestamp(11, Timestamp.from(t.occurredAt()));
            });
            jdbc.batchUpdate(INSERT_SCORE, assessments, BATCH_SIZE, (ps, a) -> {
                ps.setObject(1, primaryKey(a.transaction()));
                ps.setInt(2, a.ruleScore());
                ps.setObject(3, a.mlPrediction().map(p -> java.math.BigDecimal.valueOf(p.probability())
                        .setScale(4, java.math.RoundingMode.HALF_UP)).orElse(null), java.sql.Types.NUMERIC);
                ps.setString(4, a.mlPrediction().map(p -> p.modelVersion()).orElse(null));
                ps.setInt(5, a.riskScore());
                ps.setString(6, a.decision().name());
                ps.setTimestamp(7, Timestamp.from(a.scoredAt()));
            });
            jdbc.batchUpdate(INSERT_HIT, hits, BATCH_SIZE, (ps, h) -> {
                ps.setObject(1, h.transactionPk());
                ps.setString(2, h.hit().code());
                ps.setInt(3, h.hit().weight());
                ps.setString(4, h.hit().reason());
            });
            outbox.append(alerts.stream().map(alertEvents::toOutbox).toList());
        });
    }

    @Override
    public List<TransactionHistoryEntry> recentForAccount(String accountId, int limit) {
        return jdbc.query(HISTORY_SQL, (rs, i) -> new TransactionHistoryEntry(
                rs.getString("transaction_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("merchant_id"),
                rs.getString("merchant_category_code"),
                rs.getString("country"),
                rs.getString("channel"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getInt("risk_score"),
                rs.getString("decision")), accountId, limit);
    }

    private record RuleHitRow(UUID transactionPk, RuleHit hit) {}
}
