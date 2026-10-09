package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RiskStatus;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use case: score one Kafka poll's worth of transactions.
 *
 * <ol>
 *   <li>drop events already processed, and duplicates within the batch;
 *   <li>look up which accounts are flagged high-risk (one pipelined cache round trip);
 *   <li>record activity + evaluate rules per transaction; a DECLINE flags the account for the
 *       <i>rest of this batch</i> too;
 *   <li>persist <b>all</b> assessments in one DB transaction (JDBC batch);
 *   <li>flag newly declined accounts in the cache (after the commit: the cache only reflects
 *       committed state);
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
    private final HighRiskAccountCache riskCache;
    private final AlertPublisher alerts;
    private final Clock clock;

    public ScoreTransactionService(
            ProcessedEventStore processed,
            AccountActivityStore activityStore,
            RuleEngine ruleEngine,
            AssessmentRepository repository,
            HighRiskAccountCache riskCache,
            AlertPublisher alerts,
            Clock clock) {
        this.processed = processed;
        this.activityStore = activityStore;
        this.ruleEngine = ruleEngine;
        this.repository = repository;
        this.riskCache = riskCache;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** @return assessments for the transactions that were actually (re)scored */
    public List<RiskAssessment> scoreBatch(List<Transaction> batch) {
        List<Transaction> fresh = freshTransactions(batch);
        if (fresh.isEmpty()) {
            return List.of();
        }

        Set<String> flagged = new HashSet<>(riskCache.flaggedAmong(
                fresh.stream().map(Transaction::accountId).collect(Collectors.toSet())));
        List<RiskAssessment> assessments = new ArrayList<>(fresh.size());
        for (Transaction tx : fresh) {
            RiskAssessment assessment = assess(tx, flagged.contains(tx.accountId()));
            if (assessment.decision() == Decision.DECLINE) {
                flagged.add(tx.accountId()); // later records of this account in the same poll
            }
            assessments.add(assessment);
        }

        repository.saveAll(assessments);
        assessments.stream()
                .filter(a -> a.decision() == Decision.DECLINE)
                .forEach(a -> riskCache.put(new RiskStatus.Flagged(toHighRisk(a))));
        assessments.stream().filter(RiskAssessment::raisesAlert).forEach(alerts::publish);
        fresh.forEach(tx -> processed.markProcessed(tx.eventId()));
        return assessments;
    }

    private RiskAssessment assess(Transaction tx, boolean knownHighRisk) {
        var activity = activityStore.recordAndGet(tx).withKnownHighRisk(knownHighRisk);
        return RiskAssessment.fromRules(tx, ruleEngine.evaluate(tx, activity), clock.instant());
    }

    private static HighRiskAccount toHighRisk(RiskAssessment a) {
        String reason = a.hits().stream().map(RuleHit::code).collect(Collectors.joining(","));
        return new HighRiskAccount(a.transaction().accountId(), a.riskScore(), reason, a.scoredAt());
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
