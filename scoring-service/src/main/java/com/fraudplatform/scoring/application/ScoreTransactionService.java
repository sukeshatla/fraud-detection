package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use case: score one Kafka poll's worth of transactions.
 *
 * <ol>
 *   <li>drop events already processed, and duplicates within the batch;
 *   <li>record activity + evaluate rules per transaction;
 *   <li>persist <b>all</b> assessments in one DB transaction (JDBC batch);
 *   <li>publish alerts for REVIEW/DECLINE;
 *   <li>mark events processed.
 * </ol>
 *
 * <p><b>Delivery semantics.</b> Kafka delivers at-least-once. The processed marker is written
 * <i>last</i>; if we crash before that, the batch is redelivered and processed again. Every step is
 * idempotent (activity keyed by eventId, {@code ON CONFLICT DO NOTHING}, deterministic alert ids),
 * so the repeat is harmless. Writing the marker <i>first</i> would turn a crash into a silently
 * lost transaction, which is worse for fraud.
 */
public class ScoreTransactionService {

    private final ProcessedEventStore processed;
    private final AccountActivityStore activityStore;
    private final RuleEngine ruleEngine;
    private final AssessmentRepository repository;
    private final AlertPublisher alerts;
    private final Clock clock;

    public ScoreTransactionService(
            ProcessedEventStore processed,
            AccountActivityStore activityStore,
            RuleEngine ruleEngine,
            AssessmentRepository repository,
            AlertPublisher alerts,
            Clock clock) {
        this.processed = processed;
        this.activityStore = activityStore;
        this.ruleEngine = ruleEngine;
        this.repository = repository;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** @return assessments for the transactions that were actually (re)scored */
    public List<RiskAssessment> scoreBatch(List<Transaction> batch) {
        List<Transaction> fresh = freshTransactions(batch);
        if (fresh.isEmpty()) {
            return List.of();
        }

        List<RiskAssessment> assessments = fresh.stream().map(this::assess).toList();
        repository.saveAll(assessments);
        assessments.stream().filter(RiskAssessment::raisesAlert).forEach(alerts::publish);
        fresh.forEach(tx -> processed.markProcessed(tx.eventId()));
        return assessments;
    }

    private RiskAssessment assess(Transaction tx) {
        return RiskAssessment.fromRules(tx, ruleEngine.evaluate(tx, activityStore.recordAndGet(tx)), clock.instant());
    }

    private List<Transaction> freshTransactions(List<Transaction> batch) {
        Map<Object, Transaction> byEventId = new LinkedHashMap<>(); // keeps partition order
        for (Transaction tx : batch) {
            if (!byEventId.containsKey(tx.eventId()) && !processed.isProcessed(tx.eventId())) {
                byEventId.put(tx.eventId(), tx);
            }
        }
        return List.copyOf(byEventId.values());
    }
}
