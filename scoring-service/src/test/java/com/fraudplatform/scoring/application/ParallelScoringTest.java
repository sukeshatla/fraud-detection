package com.fraudplatform.scoring.application;

import static com.fraudplatform.scoring.domain.TransactionBuilder.NOW;
import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.ScoreBlender;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-009-01: within one Kafka poll, different accounts are scored in parallel on virtual threads,
 * while each account's transactions stay strictly in order (velocity windows depend on it).
 * The same idea as Kafka partitions, applied inside a batch.
 */
class ParallelScoringTest {

    static final int ACCOUNTS = 20;
    static final int TX_PER_ACCOUNT = 3;
    static final int REDIS_LATENCY_MS = 20; // simulated round trip of the activity Lua call

    /** Slow activity store that records the per-account call order and peak concurrency. */
    static final class SlowActivityStore implements AccountActivityStore {
        final Map<String, List<String>> callOrder = new ConcurrentHashMap<>();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();

        @Override
        public AccountActivity recordAndGet(Transaction tx) {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(REDIS_LATENCY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            callOrder.computeIfAbsent(tx.accountId(), a -> new CopyOnWriteArrayList<>()).add(tx.transactionId());
            return AccountActivity.none();
        }
    }

    @Test
    @DisplayName("AC-009-01: 20 accounts × 3 txns: parallel ≈ 3 round trips, sequential ≈ 60; order kept per account and in the result")
    void parallelAcrossAccountsSequentialWithin() {
        List<Transaction> batch = interleavedBatch();

        SlowActivityStore sequentialStore = new SlowActivityStore();
        long sequentialMs = timed(() -> service(sequentialStore, 1).scoreBatch(batch));

        SlowActivityStore parallelStore = new SlowActivityStore();
        List<RiskAssessment>[] result = new List[1];
        long parallelMs = timed(() -> result[0] = service(parallelStore, 64).scoreBatch(batch));

        System.out.printf("%n%d txns over %d accounts, %d ms per activity call: sequential %d ms, parallel %d ms (peak concurrency %d)%n",
                batch.size(), ACCOUNTS, REDIS_LATENCY_MS, sequentialMs, parallelMs, parallelStore.peak.get());

        assertThat(parallelMs * 5).isLessThan(sequentialMs);
        assertThat(parallelStore.peak.get()).isGreaterThan(1).isLessThanOrEqualTo(ACCOUNTS); // never 2 calls for one account
        parallelStore.callOrder.forEach((account, order) ->
                assertThat(order).as(account).containsExactly(account + "-0", account + "-1", account + "-2"));
        assertThat(result[0]).extracting(a -> a.transaction().transactionId())
                .containsExactlyElementsOf(batch.stream().map(Transaction::transactionId).toList());
    }

    @Test
    @DisplayName("AC-009-02: concurrency is bounded: maxConcurrentAccounts caps the fan-out")
    void fanOutIsBounded() {
        SlowActivityStore store = new SlowActivityStore();

        service(store, 4).scoreBatch(interleavedBatch());

        assertThat(store.peak.get()).isLessThanOrEqualTo(4);
    }

    /** acc-0-tx0, acc-1-tx0, …, acc-0-tx1, …: the arrival order of a real poll across partitions' keys. */
    private static List<Transaction> interleavedBatch() {
        List<Transaction> batch = new ArrayList<>();
        for (int i = 0; i < TX_PER_ACCOUNT; i++) {
            for (int a = 0; a < ACCOUNTS; a++) {
                String account = "acc-" + a;
                batch.add(aTransaction().eventId(UUID.randomUUID()).accountId(account).transactionId(account + "-" + i)
                        .occurredAt(NOW.plusSeconds(i)).build());
            }
        }
        return batch;
    }

    private static ScoreTransactionService service(AccountActivityStore store, int maxConcurrentAccounts) {
        return new ScoreTransactionService(mock(ProcessedEventStore.class), store, new RuleEngine(List.of()),
                mock(AssessmentRepository.class), new FakeHighRiskAccountCache(), (tx, a) -> Optional.empty(),
                new ScoreBlender(0.6), mock(ScoringMetrics.class), Clock.fixed(NOW, ZoneOffset.UTC), maxConcurrentAccounts);
    }

    private static long timed(Runnable work) {
        long start = System.nanoTime();
        work.run();
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
