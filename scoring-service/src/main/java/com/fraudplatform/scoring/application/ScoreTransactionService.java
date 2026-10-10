package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RiskStatus;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.ScoreBlender;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Use case: score one Kafka poll's worth of transactions.
 *
 * <ol>
 *   <li>drop events already processed, and duplicates within the batch;
 *   <li>look up which accounts are flagged high-risk (one pipelined cache round trip);
 *   <li>record activity, evaluate rules, ask the model (bulkheaded; rules-only if unavailable)
 *       and blend; a DECLINE flags the account for the <i>rest of this batch</i> too.
 *       <b>Different accounts run in parallel on virtual threads; one account's transactions run
 *       sequentially, in order</b> (velocity windows depend on it), the same guarantee Kafka
 *       partitions give across consumers, applied inside a batch;
 *   <li>persist <b>all</b> assessments <b>and</b> enqueue the alerts (transactional outbox) in one DB
 *       transaction: an alert can't be lost or announced for an assessment that didn't commit;
 *   <li>flag newly declined accounts in the cache (after the commit: the cache only reflects
 *       committed state);
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
    private final MlScorer mlScorer;
    private final ScoreBlender blender;
    private final ScoringMetrics metrics;
    private final Clock clock;
    private final int maxConcurrentAccounts;

    public ScoreTransactionService(
            ProcessedEventStore processed,
            AccountActivityStore activityStore,
            RuleEngine ruleEngine,
            AssessmentRepository repository,
            HighRiskAccountCache riskCache,
            MlScorer mlScorer,
            ScoreBlender blender,
            ScoringMetrics metrics,
            Clock clock,
            int maxConcurrentAccounts) {
        this.processed = processed;
        this.activityStore = activityStore;
        this.ruleEngine = ruleEngine;
        this.repository = repository;
        this.riskCache = riskCache;
        this.mlScorer = mlScorer;
        this.blender = blender;
        this.metrics = metrics;
        this.clock = clock;
        this.maxConcurrentAccounts = maxConcurrentAccounts;
    }

    /** @return assessments for the transactions that were actually (re)scored */
    public List<RiskAssessment> scoreBatch(List<Transaction> batch) {
        List<Transaction> fresh = freshTransactions(batch);
        if (fresh.isEmpty()) {
            return List.of();
        }
        long started = System.nanoTime();

        Map<String, List<Transaction>> byAccount = fresh.stream()
                .collect(Collectors.groupingBy(Transaction::accountId, LinkedHashMap::new, Collectors.toList()));
        Set<String> flagged = ConcurrentHashMap.newKeySet();
        flagged.addAll(riskCache.flaggedAmong(byAccount.keySet()));

        Map<UUID, RiskAssessment> byEvent = new ConcurrentHashMap<>();
        forEachAccount(byAccount.values(), transactions -> {
            for (Transaction tx : transactions) { // in order, within one account
                RiskAssessment assessment = assess(tx, flagged.contains(tx.accountId()));
                if (assessment.decision() == Decision.DECLINE) {
                    flagged.add(tx.accountId()); // later records of this account in the same poll
                }
                byEvent.put(tx.eventId(), assessment);
            }
        });
        List<RiskAssessment> assessments = fresh.stream().map(tx -> byEvent.get(tx.eventId())).toList();

        repository.saveAll(assessments, assessments.stream().filter(RiskAssessment::raisesAlert).toList());
        assessments.stream()
                .filter(a -> a.decision() == Decision.DECLINE)
                .forEach(a -> riskCache.put(new RiskStatus.Flagged(toHighRisk(a))));
        fresh.forEach(tx -> processed.markProcessed(tx.eventId()));
        metrics.assessed(assessments);
        metrics.batchProcessed(fresh.size(), Duration.ofNanos(System.nanoTime() - started));
        return assessments;
    }

    /**
     * Runs {@code work} once per account: inline for a single account, otherwise on virtual threads
     * with at most {@code maxConcurrentAccounts} in flight (Semaphore). Virtual threads remove the
     * thread limit, not the downstream limit. Any failure fails the whole batch, which is then
     * redelivered (all side effects so far are idempotent).
     */
    private void forEachAccount(Collection<List<Transaction>> groups, Consumer<List<Transaction>> work) {
        if (groups.size() == 1 || maxConcurrentAccounts <= 1) {
            groups.forEach(work);
            return;
        }
        Semaphore permits = new Semaphore(maxConcurrentAccounts);
        List<Future<?>> futures = new ArrayList<>(groups.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<Transaction> group : groups) {
                futures.add(executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        work.accept(group);
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Future<?> future : futures) {
                await(future);
            }
        }
    }

    private static void await(Future<?> future) {
        try {
            future.get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while scoring a batch", e);
        }
    }

    private RiskAssessment assess(Transaction tx, boolean knownHighRisk) {
        var activity = activityStore.recordAndGet(tx).withKnownHighRisk(knownHighRisk);
        return RiskAssessment.of(tx, ruleEngine.evaluate(tx, activity), mlScorer.score(tx, activity), blender,
                clock.instant());
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
