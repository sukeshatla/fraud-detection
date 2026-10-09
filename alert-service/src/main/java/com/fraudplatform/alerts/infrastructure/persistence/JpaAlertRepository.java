package com.fraudplatform.alerts.infrastructure.persistence;

import com.fraudplatform.alerts.application.AlertDetails;
import com.fraudplatform.alerts.application.AlertNotFoundException;
import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.alerts.application.AlertResolution;
import com.fraudplatform.alerts.application.AlertStats;
import com.fraudplatform.alerts.application.AlertView;
import com.fraudplatform.alerts.application.AuditEntry;
import com.fraudplatform.alerts.application.Cursor;
import com.fraudplatform.alerts.application.KeysetPage;
import com.fraudplatform.alerts.application.KeysetQuery;
import com.fraudplatform.alerts.application.NewAlert;
import com.fraudplatform.alerts.application.OffsetPage;
import com.fraudplatform.alerts.application.PageQuery;
import com.fraudplatform.alerts.application.RuleHitView;
import com.fraudplatform.alerts.application.StaleAlertException;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.infrastructure.kafka.AlertResolvedEvents;
import com.fraudplatform.messaging.outbox.OutboxWriter;
import com.fraudplatform.alerts.domain.Severity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Alert persistence. Writes the ingest path with JDBC ({@code ON CONFLICT DO NOTHING}), and reads
 * and reviews with JPA.
 *
 * <p><b>No N+1:</b> a page of alerts costs a constant number of statements: the page, one
 * {@code WHERE alert_id IN (…)} for all their rule hits, and (offset paging only) one count.
 */
public class JpaAlertRepository implements AlertRepository {

    static final String INSERT_ALERT = """
            INSERT INTO alert (id, transaction_id, account_id, amount, currency, merchant_id, merchant_category_code,
                               country, channel, occurred_at, rule_score, risk_score, ml_probability, model_version,
                               decision, severity, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 0, ?, ?)
            ON CONFLICT DO NOTHING""";

    static final String INSERT_HIT = "INSERT INTO alert_rule_hit (alert_id, code, weight, reason) VALUES (?, ?, ?, ?)";

    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final OutboxWriter outbox;
    private final AlertResolvedEvents resolutionEvents;

    public JpaAlertRepository(EntityManager em, JdbcTemplate jdbc, TransactionTemplate tx, OutboxWriter outbox,
            AlertResolvedEvents resolutionEvents) {
        this.em = em;
        this.jdbc = jdbc;
        this.tx = tx;
        this.outbox = outbox;
        this.resolutionEvents = resolutionEvents;
        this.readTx = new TransactionTemplate(tx.getTransactionManager());
        this.readTx.setReadOnly(true);
    }

    @Override
    public boolean insertIfAbsent(NewAlert a) {
        Boolean inserted = tx.execute(status -> {
            int rows = jdbc.update(INSERT_ALERT, a.id(), a.transactionId(), a.accountId(), a.amount(), a.currency(),
                    a.merchantId(), a.merchantCategoryCode(), a.country(), a.channel(), Timestamp.from(a.occurredAt()),
                    a.ruleScore(), a.riskScore(), a.mlProbability(), a.modelVersion(), a.decision(), a.severity().name(),
                    Timestamp.from(a.createdAt()), Timestamp.from(a.createdAt()));
            if (rows == 0) {
                return false; // already there: redelivery or replay
            }
            jdbc.batchUpdate(INSERT_HIT, a.ruleHits(), a.ruleHits().size(), (ps, h) -> {
                ps.setObject(1, a.id());
                ps.setString(2, h.code());
                ps.setInt(3, h.weight());
                ps.setString(4, h.reason());
            });
            return true;
        });
        return Boolean.TRUE.equals(inserted);
    }

    @Override
    public Optional<AlertView> findById(UUID id) {
        return readTx.execute(s -> Optional.ofNullable(em.find(AlertEntity.class, id)).map(a -> toView(a, hitsFor(List.of(id)))));
    }

    @Override
    public Optional<AlertDetails> findDetails(UUID id) {
        return readTx.execute(s -> Optional.ofNullable(em.find(AlertEntity.class, id)).map(a -> {
            List<AuditEntry> history = em.createQuery("""
                    select e from AlertEventEntity e where e.alertId = :id order by e.changedAt, e.id""", AlertEventEntity.class)
                    .setParameter("id", id)
                    .getResultStream()
                    .map(e -> new AuditEntry(e.getFromStatus(), e.getToStatus(), e.getActor(), e.getChangedAt()))
                    .toList();
            return new AlertDetails(toView(a, hitsFor(List.of(id))), history);
        }));
    }

    @Override
    public OffsetPage findPage(PageQuery q) {
        return readTx.execute(s -> {
            String where = " where a.status = :status" + (q.severity().isPresent() ? " and a.severity = :severity" : "");
            TypedQuery<AlertEntity> page = em.createQuery(
                    "select a from AlertEntity a" + where + " order by a.createdAt desc, a.id desc", AlertEntity.class);
            TypedQuery<Long> count = em.createQuery("select count(a) from AlertEntity a" + where, Long.class);
            for (Query query : List.<Query>of(page, count)) {
                query.setParameter("status", q.status());
                q.severity().ifPresent(sev -> query.setParameter("severity", sev));
            }
            List<AlertEntity> alerts = page.setFirstResult(q.page() * q.size()).setMaxResults(q.size()).getResultList();
            return new OffsetPage(toViews(alerts), q.page(), q.size(), count.getSingleResult());
        });
    }

    /**
     * Keyset ("seek") pagination with a row-value comparison. PostgreSQL walks
     * {@code ix_alert_queue} from the cursor and stops after {@code size + 1} rows, no matter how
     * deep the client is. {@code OFFSET n} would read and discard n rows.
     */
    @Override
    public KeysetPage findAfter(KeysetQuery q) {
        return readTx.execute(s -> {
            String sql = "SELECT * FROM alert WHERE status = :status"
                    + (q.severity().isPresent() ? " AND severity = :severity" : "")
                    + (q.after().isPresent() ? " AND (created_at, id) < (CAST(:createdAt AS timestamptz), CAST(:id AS uuid))" : "")
                    + " ORDER BY created_at DESC, id DESC LIMIT :limit";
            Query query = em.createNativeQuery(sql, AlertEntity.class)
                    .setParameter("status", q.status().name())
                    .setParameter("limit", q.size() + 1); // one extra row tells us whether a next page exists
            q.severity().ifPresent(sev -> query.setParameter("severity", sev.name()));
            q.after().ifPresent(c -> query.setParameter("createdAt", Timestamp.from(c.createdAt())).setParameter("id", c.id()));

            @SuppressWarnings("unchecked")
            List<AlertEntity> rows = query.getResultList();
            boolean hasMore = rows.size() > q.size();
            List<AlertView> items = toViews(hasMore ? rows.subList(0, q.size()) : rows);
            return new KeysetPage(items, hasMore ? Optional.of(Cursor.of(items.getLast())) : Optional.empty());
        });
    }

    @Override
    public AlertView transition(UUID id, long expectedVersion, AlertStatus to, String actor, Instant at,
            Optional<AlertResolution> resolution) {
        try {
            return tx.execute(s -> {
                AlertEntity alert = em.find(AlertEntity.class, id);
                if (alert == null) {
                    throw new AlertNotFoundException(id);
                }
                if (alert.getVersion() != expectedVersion) {
                    throw new StaleAlertException(toView(alert, hitsFor(List.of(id))));
                }
                AlertStatus from = alert.getStatus();
                from.requireTransitionTo(to);
                alert.moveTo(to, at);
                em.persist(new AlertEventEntity(id, from, to, actor, at));
                resolution.ifPresent(r -> outbox.append(List.of(resolutionEvents.toOutbox(r)))); // same transaction
                // UPDATE alert SET …, version = v+1 WHERE id = ? AND version = v. 0 rows → OptimisticLockException
                em.flush();
                return toView(alert, hitsFor(List.of(id)));
            });
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            // Another transaction committed first (possible when the Redis lock was bypassed or expired).
            throw new StaleAlertException(findById(id).orElseThrow(() -> new AlertNotFoundException(id)));
        }
    }

    /** One pass over the table with FILTER clauses. At scale: a rollup table or materialized view. */
    @Override
    public AlertStats stats(Instant since) {
        Timestamp t = Timestamp.from(since);
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status = 'OPEN' AND severity = 'HIGH')   AS open_high,
                       count(*) FILTER (WHERE status = 'OPEN' AND severity = 'MEDIUM') AS open_medium,
                       count(*) FILTER (WHERE status = 'OPEN' AND severity = 'LOW')    AS open_low,
                       count(*) FILTER (WHERE status = 'UNDER_REVIEW')                 AS under_review,
                       count(*) FILTER (WHERE created_at >= ?)                          AS raised,
                       count(*) FILTER (WHERE status = 'CONFIRMED_FRAUD' AND updated_at >= ?) AS confirmed,
                       count(*) FILTER (WHERE status = 'FALSE_POSITIVE'  AND updated_at >= ?) AS dismissed
                FROM alert""", (rs, i) -> new AlertStats(
                Map.of(Severity.HIGH, rs.getLong("open_high"), Severity.MEDIUM, rs.getLong("open_medium"),
                        Severity.LOW, rs.getLong("open_low")),
                rs.getLong("under_review"), rs.getLong("raised"), rs.getLong("confirmed"), rs.getLong("dismissed"), since),
                t, t, t);
    }

    private List<AlertView> toViews(List<AlertEntity> alerts) {
        if (alerts.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RuleHitView>> hits = hitsFor(alerts.stream().map(AlertEntity::getId).toList());
        return alerts.stream().map(a -> toView(a, hits)).toList();
    }

    /** ONE query for the hits of every alert on the page: this is what defeats N+1. */
    private Map<UUID, List<RuleHitView>> hitsFor(Collection<UUID> alertIds) {
        return em.createQuery("""
                        select h from AlertRuleHitEntity h where h.alert.id in :ids order by h.weight desc, h.code""",
                        AlertRuleHitEntity.class)
                .setParameter("ids", alertIds)
                .getResultStream()
                .collect(Collectors.groupingBy(h -> h.getAlert().getId(), // reading a proxy's id doesn't load it
                        Collectors.mapping(h -> new RuleHitView(h.getCode(), h.getWeight(), h.getReason()), Collectors.toList())));
    }

    private static AlertView toView(AlertEntity a, Map<UUID, List<RuleHitView>> hits) {
        return new AlertView(a.getId(), a.getTransactionId(), a.getAccountId(), a.getAmount(), a.getCurrency(),
                a.getMerchantId(), a.getMerchantCategoryCode(), a.getCountry(), a.getChannel(), a.getOccurredAt(),
                a.getRuleScore(), a.getRiskScore(), a.getMlProbability(), a.getModelVersion(), a.getDecision(),
                a.getSeverity(), a.getStatus(), a.getVersion(), a.getCreatedAt(), a.getUpdatedAt(),
                hits.getOrDefault(a.getId(), List.of()));
    }
}
