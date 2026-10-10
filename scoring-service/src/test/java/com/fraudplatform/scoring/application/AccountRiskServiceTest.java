package com.fraudplatform.scoring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccountRiskServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T18:00:00Z");
    private static final HighRiskAccount FLAG = new HighRiskAccount("acc-1", 85, "HIGH_AMOUNT,GEO_VELOCITY", NOW);

    private final FakeHighRiskAccountCache cache = new FakeHighRiskAccountCache();

    /** Counts loads and simulates a slow DB so concurrent callers really overlap. */
    private static final class CountingSource implements HighRiskAccountSource {
        final AtomicInteger loads = new AtomicInteger();
        final Optional<HighRiskAccount> answer;

        CountingSource(Optional<HighRiskAccount> answer) {
            this.answer = answer;
        }

        @Override
        public Optional<HighRiskAccount> findActiveFlag(String accountId) {
            loads.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return answer;
        }

        @Override
        public void recordClearance(String accountId, Instant at) {}
    }

    private AccountRiskService service(HighRiskAccountSource source) {
        return new AccountRiskService(cache, source, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMillis(300), Duration.ofMillis(10));
    }

    @Test
    @DisplayName("AC-005-03: cache hit → no DB access")
    void hitSkipsDatabase() {
        cache.put(new RiskStatus.Flagged(FLAG));
        CountingSource source = new CountingSource(Optional.empty());

        assertThat(service(source).riskOf("acc-1")).isEqualTo(new RiskStatus.Flagged(FLAG));
        assertThat(source.loads).hasValue(0);
    }

    @Test
    @DisplayName("AC-005-03: miss → load from DB and populate the cache (cache-aside)")
    void missLoadsAndPopulates() {
        CountingSource source = new CountingSource(Optional.of(FLAG));

        assertThat(service(source).riskOf("acc-1").isHighRisk()).isTrue();
        assertThat(cache.get("acc-1")).contains(new RiskStatus.Flagged(FLAG));
        assertThat(source.loads).hasValue(1);
    }

    @Test
    @DisplayName("AC-005-03: clean accounts are cached too (negative caching, against penetration)")
    void negativeResultIsCached() {
        CountingSource source = new CountingSource(Optional.empty());
        AccountRiskService service = service(source);

        service.riskOf("acc-1");
        service.riskOf("acc-1");

        assertThat(cache.get("acc-1")).contains(new RiskStatus.Clear("acc-1"));
        assertThat(source.loads).hasValue(1);
    }

    @Test
    @DisplayName("AC-005-04: 100 concurrent misses in one JVM → exactly one DB load (single-flight)")
    void singleFlightInProcess() throws Exception {
        CountingSource source = new CountingSource(Optional.of(FLAG));
        AccountRiskService service = service(source);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RiskStatus>> results = IntStream.range(0, 100)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return service.riskOf("acc-1");
                    }))
                    .toList();
            start.countDown();
            for (Future<RiskStatus> r : results) {
                assertThat(r.get().isHighRisk()).isTrue();
            }
        }
        assertThat(source.loads).hasValue(1);
    }

    @Test
    @DisplayName("AC-005-04: another instance holds the loader lock → wait for its result instead of hitting the DB")
    void waitsForOtherInstance() throws Exception {
        CountingSource source = new CountingSource(Optional.of(FLAG));
        cache.tryAcquireLoadLock("acc-1"); // "another instance" is loading
        Thread.ofVirtual().start(() -> {
            sleep(50);
            cache.put(new RiskStatus.Flagged(FLAG)); // …and publishes its result
        });

        assertThat(service(source).riskOf("acc-1").isHighRisk()).isTrue();
        assertThat(source.loads).hasValue(0);
    }

    @Test
    @DisplayName("AC-005-04: if the other loader never finishes, fall back to the DB after the wait budget")
    void fallsBackAfterWaitBudget() {
        CountingSource source = new CountingSource(Optional.empty());
        cache.tryAcquireLoadLock("acc-1"); // lock held, nobody ever fills the cache

        assertThat(service(source).riskOf("acc-1").isHighRisk()).isFalse();
        assertThat(source.loads).hasValue(1);
    }

    @Test
    @DisplayName("AC-005-04: double-check after acquiring the loader lock: a value filled meanwhile is NOT reloaded")
    void doubleCheckAfterAcquiringLock() {
        CountingSource source = new CountingSource(Optional.of(FLAG));
        // Interleaving found by DistributedSingleFlightIT: our first cache read misses, then another
        // instance fills the cache AND releases the lock before we try to take it.
        HighRiskAccountCache racing = new FakeHighRiskAccountCache() {
            private int reads;

            @Override
            public Optional<RiskStatus> get(String accountId) {
                if (reads++ == 0) {
                    super.put(new RiskStatus.Flagged(FLAG)); // the other pod finishes right after our miss
                    return Optional.empty();
                }
                return super.get(accountId);
            }
        };
        AccountRiskService service = new AccountRiskService(racing, source, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMillis(300), Duration.ofMillis(10));

        assertThat(service.riskOf("acc-1").isHighRisk()).isTrue();
        assertThat(source.loads).as("must not hit the database again").hasValue(0);
    }

    @Test
    @DisplayName("AC-005-05: clearing updates the source of truth FIRST, then evicts")
    void clearUpdatesSourceThenEvicts() {
        HighRiskAccountSource source = mock(HighRiskAccountSource.class);
        HighRiskAccountCache mockCache = mock(HighRiskAccountCache.class);
        AccountRiskService service = new AccountRiskService(mockCache, source, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMillis(300), Duration.ofMillis(10));

        service.clear("acc-1");

        InOrder order = inOrder(source, mockCache);
        order.verify(source).recordClearance("acc-1", NOW);
        order.verify(mockCache).evict("acc-1");
    }

    @Test
    @DisplayName("AC-005-07: lists flagged accounts from the cache")
    void listsFlagged() {
        cache.put(new RiskStatus.Flagged(FLAG));
        cache.put(new RiskStatus.Clear("acc-2"));

        assertThat(service(new CountingSource(Optional.empty())).listFlagged(10)).containsExactly(FLAG);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
