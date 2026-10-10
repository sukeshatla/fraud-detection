package com.fraudplatform.alerts;

import static com.fraudplatform.alerts.application.AlertFixtures.newAlert;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.alerts.application.AlertLock;
import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.alerts.application.ReviewAlertService;
import com.fraudplatform.alerts.application.StaleAlertException;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** AC-007-05..08: two-layer concurrent-write protection under real contention. */
@IntegrationTest
class AlertConcurrencyIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AlertRepository repository;


    @Autowired
    private com.fraudplatform.alerts.application.AlertChangeBus changes;

    @Autowired
    private com.fraudplatform.alerts.application.AlertMetrics metrics;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-007-07: 50 concurrent PATCHes on one alert → exactly 1 × 200, 49 × 409, 1 audit row")
    void fiftyConcurrentPatches() throws Exception {
        UUID id = newOpenAlert();

        List<Integer> statuses = race(50, () -> mvc.patch().uri("/api/v1/alerts/" + id)
                .header("X-Actor", "analyst-" + Thread.currentThread().threadId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNDER_REVIEW\",\"version\":0}")
                .exchange().getResponse().getStatus());

        Map<Integer, Long> histogram = statuses.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(histogram).containsOnlyKeys(200, 409).containsEntry(200, 1L).containsEntry(409, 49L);
        assertThat(auditRows(id)).isEqualTo(1);
        assertThat(repository.findById(id).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-007-06: with layer 1 BYPASSED, optimistic locking alone still lets exactly one writer win")
    void layerTwoHoldsWithoutLock() throws Exception {
        UUID id = newOpenAlert();
        AlertLock noLock = alertId -> Optional.of(() -> {}); // simulates an expired / failed Redis lease
        ReviewAlertService unguarded = new ReviewAlertService(repository, noLock, changes, metrics, Clock.systemUTC());

        List<String> outcomes = race(50, () -> {
            try {
                unguarded.review(id, AlertStatus.UNDER_REVIEW, 0, "analyst");
                return "OK";
            } catch (StaleAlertException e) {
                return "STALE";
            }
        });

        assertThat(outcomes).containsOnlyOnce("OK").containsOnly("OK", "STALE");
        assertThat(auditRows(id)).isEqualTo(1);
    }

    private UUID newOpenAlert() {
        UUID id = UUID.randomUUID();
        repository.insertIfAbsent(newAlert(id, "txn-" + id, "acc-" + id, Instant.now()));
        return id;
    }

    private int auditRows(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM alert_event WHERE alert_id = ?", Integer.class, id);
    }

    /** Runs {@code n} copies of {@code task}, released simultaneously by a start gate. */
    private static <T> List<T> race(int n, Callable<T> task) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
        }
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get());
        }
        return results;
    }
}
