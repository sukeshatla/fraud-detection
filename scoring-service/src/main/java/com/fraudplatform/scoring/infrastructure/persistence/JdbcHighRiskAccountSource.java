package com.fraudplatform.scoring.infrastructure.persistence;

import com.fraudplatform.scoring.application.HighRiskAccountSource;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Source of truth for high-risk accounts: recent DECLINEs not superseded by a clearance. */
public class JdbcHighRiskAccountSource implements HighRiskAccountSource {

    /** Walks the (account_id, occurred_at DESC) index newest-first and stops at the first match. */
    static final String ACTIVE_FLAG_SQL = """
            SELECT t.account_id, r.risk_score, r.scored_at,
                   (SELECT string_agg(h.rule_code, ',' ORDER BY h.rule_code)
                      FROM rule_hit h WHERE h.transaction_pk = t.id) AS reason
            FROM transaction t
            JOIN risk_score r ON r.transaction_pk = t.id
            LEFT JOIN account_risk_override o ON o.account_id = t.account_id
            WHERE t.account_id = ?
              AND r.decision = 'DECLINE'
              AND r.scored_at > ?
              AND (o.cleared_at IS NULL OR r.scored_at > o.cleared_at)
            ORDER BY t.occurred_at DESC
            LIMIT 1""";

    static final String UPSERT_CLEARANCE = """
            INSERT INTO account_risk_override (account_id, cleared_at) VALUES (?, ?)
            ON CONFLICT (account_id) DO UPDATE SET cleared_at = EXCLUDED.cleared_at""";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration flagWindow;

    public JdbcHighRiskAccountSource(JdbcTemplate jdbc, Clock clock, Duration flagWindow) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.flagWindow = flagWindow;
    }

    @Override
    public Optional<HighRiskAccount> findActiveFlag(String accountId) {
        Timestamp since = Timestamp.from(clock.instant().minus(flagWindow));
        return jdbc.query(ACTIVE_FLAG_SQL, (rs, i) -> new HighRiskAccount(
                rs.getString("account_id"),
                rs.getInt("risk_score"),
                rs.getString("reason") == null ? "" : rs.getString("reason"),
                rs.getTimestamp("scored_at").toInstant()), accountId, since).stream().findFirst();
    }

    @Override
    public void recordClearance(String accountId, Instant at) {
        jdbc.update(UPSERT_CLEARANCE, accountId, Timestamp.from(at));
    }
}
