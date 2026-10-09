package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Clock;
import java.util.Optional;

/**
 * Use case: score one transaction.
 *
 * <p><b>Delivery semantics.</b> Kafka delivers at-least-once. The processed marker is written
 * <i>last</i>; if we crash before that, the event is redelivered and processed again. Every step is
 * idempotent (activity keyed by eventId, alerts deduped by transactionId downstream), so the
 * repeat is harmless. Writing the marker <i>first</i> would turn a crash into a silently lost
 * transaction, which is worse for fraud.
 */
public class ScoreTransactionService {

    private final ProcessedEventStore processed;
    private final AccountActivityStore activityStore;
    private final RuleEngine ruleEngine;
    private final AlertPublisher alerts;
    private final Clock clock;

    public ScoreTransactionService(
            ProcessedEventStore processed,
            AccountActivityStore activityStore,
            RuleEngine ruleEngine,
            AlertPublisher alerts,
            Clock clock) {
        this.processed = processed;
        this.activityStore = activityStore;
        this.ruleEngine = ruleEngine;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** @return the assessment, or empty if this event was already processed */
    public Optional<RiskAssessment> score(Transaction transaction) {
        if (processed.isProcessed(transaction.eventId())) {
            return Optional.empty();
        }

        AccountActivity activity = activityStore.recordAndGet(transaction);
        RiskAssessment assessment = RiskAssessment.fromRules(
                transaction, ruleEngine.evaluate(transaction, activity), clock.instant());

        if (assessment.raisesAlert()) {
            alerts.publish(assessment);
        }
        processed.markProcessed(transaction.eventId());
        return Optional.of(assessment);
    }
}
