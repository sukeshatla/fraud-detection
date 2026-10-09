package com.fraudplatform.scoring.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.RedisTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisHighRiskAccountCacheIT {

    @Container
    static final GenericContainer<?> REDIS = Containers.redis();

    private static StringRedisTemplate redis;
    private static SimpleMeterRegistry meters;
    private static RedisHighRiskAccountCache cache;

    @BeforeAll
    static void connect() {
        redis = RedisTestSupport.template(REDIS);
        meters = new SimpleMeterRegistry();
        cache = new RedisHighRiskAccountCache(redis, meters, Duration.ofHours(1), Duration.ofMinutes(5), 0.10, Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("AC-005-01: flagged entry round-trips as a hash with TTL 1h ± 10%")
    void flaggedRoundTripWithJitteredTtl() {
        String acc = uniqueAccount();
        HighRiskAccount flag = new HighRiskAccount(acc, 85, "HIGH_AMOUNT,GEO_VELOCITY", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        cache.put(new RiskStatus.Flagged(flag));

        assertThat(cache.get(acc)).contains(new RiskStatus.Flagged(flag));
        assertThat(redis.getExpire("risk:account:{" + acc + "}")).isBetween(54 * 60L, 66 * 60L);
    }

    @Test
    @DisplayName("Avalanche protection: TTLs of keys written together are spread out")
    void ttlsAreJittered() {
        List<Long> ttls = IntStream.range(0, 30).mapToObj(i -> {
            String acc = uniqueAccount();
            cache.put(new RiskStatus.Flagged(new HighRiskAccount(acc, 80, "X", Instant.now())));
            return redis.getExpire("risk:account:{" + acc + "}");
        }).distinct().toList();

        assertThat(ttls).hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("AC-005-03: negative entries (CLEAR) use the short TTL")
    void negativeEntryShortTtl() {
        String acc = uniqueAccount();

        cache.put(new RiskStatus.Clear(acc));

        assertThat(cache.get(acc)).contains(new RiskStatus.Clear(acc));
        assertThat(redis.getExpire("risk:account:{" + acc + "}")).isBetween(4 * 60L, 6 * 60L);
    }

    @Test
    @DisplayName("AC-005-01: one pipelined round trip answers 'which of these accounts are flagged?'")
    void flaggedAmongIsPipelined() {
        String flagged = uniqueAccount();
        String clear = uniqueAccount();
        String unknown = uniqueAccount();
        cache.put(new RiskStatus.Flagged(new HighRiskAccount(flagged, 80, "X", Instant.now())));
        cache.put(new RiskStatus.Clear(clear));

        assertThat(cache.flaggedAmong(List.of(flagged, clear, unknown))).containsExactly(flagged);
    }

    @Test
    @DisplayName("AC-005-07: SCAN lists flagged accounts (never KEYS)")
    void scanListsFlagged() {
        String acc = uniqueAccount();
        cache.put(new RiskStatus.Flagged(new HighRiskAccount(acc, 99, "VELOCITY", Instant.now())));

        assertThat(cache.listFlagged(10_000)).extracting(HighRiskAccount::accountId).contains(acc);
    }

    @Test
    @DisplayName("AC-005-05: evict removes the entry")
    void evict() {
        String acc = uniqueAccount();
        cache.put(new RiskStatus.Clear(acc));

        cache.evict(acc);

        assertThat(cache.get(acc)).isEmpty();
    }

    @Test
    @DisplayName("AC-005-04: loader lock is exclusive and only its owner can release it")
    void loaderLock() {
        String acc = uniqueAccount();

        Optional<String> first = cache.tryAcquireLoadLock(acc);
        assertThat(first).isPresent();
        assertThat(cache.tryAcquireLoadLock(acc)).isEmpty();

        cache.releaseLoadLock(acc, "not-the-owner");
        assertThat(cache.tryAcquireLoadLock(acc)).isEmpty();

        cache.releaseLoadLock(acc, first.get());
        assertThat(cache.tryAcquireLoadLock(acc)).isPresent();
    }

    @Test
    @DisplayName("AC-005-06: hits and misses are counted")
    void countsHitsAndMisses() {
        String acc = uniqueAccount();
        double hitsBefore = count("hit");
        double missesBefore = count("miss");

        cache.get(acc);
        cache.put(new RiskStatus.Clear(acc));
        cache.get(acc);

        assertThat(count("miss") - missesBefore).isEqualTo(1.0);
        assertThat(count("hit") - hitsBefore).isEqualTo(1.0);
    }

    private static double count(String result) {
        return meters.get("cache_requests_total").tag("cache", "high_risk_account").tag("result", result).counter().count();
    }

    private static String uniqueAccount() {
        return "acc-" + UUID.randomUUID();
    }
}
