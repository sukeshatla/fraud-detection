package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Use case: "is this account high-risk?". Cache-aside with stampede protection.
 *
 * <pre>
 *   cache hit ──────────────────────────────────────────────► return
 *   miss → in-process single-flight (one loader per JVM, others join its future)
 *        → distributed lock (one loader across instances, others poll the cache briefly)
 *        → load from DB → cache (FLAGGED or CLEAR) → return
 * </pre>
 */
public class AccountRiskService {

    private final HighRiskAccountCache cache;
    private final HighRiskAccountSource source;
    private final Clock clock;
    private final Duration lockWait;
    private final Duration pollInterval;
    private final ConcurrentMap<String, CompletableFuture<RiskStatus>> inFlight = new ConcurrentHashMap<>();

    public AccountRiskService(HighRiskAccountCache cache, HighRiskAccountSource source, Clock clock,
            Duration lockWait, Duration pollInterval) {
        this.cache = cache;
        this.source = source;
        this.clock = clock;
        this.lockWait = lockWait;
        this.pollInterval = pollInterval;
    }

    public RiskStatus riskOf(String accountId) {
        Optional<RiskStatus> cached = cache.get(accountId);
        if (cached.isPresent()) {
            return cached.get();
        }

        CompletableFuture<RiskStatus> mine = new CompletableFuture<>();
        CompletableFuture<RiskStatus> leader = inFlight.putIfAbsent(accountId, mine);
        if (leader != null) {
            return join(leader); // follower: share the leader's result
        }
        try {
            RiskStatus status = loadOnce(accountId);
            mine.complete(status);
            return status;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(accountId, mine);
        }
    }

    /** Source of truth first, then evict: a later miss reloads the clearance, not the old flag. */
    public void clear(String accountId) {
        source.recordClearance(accountId, clock.instant());
        cache.evict(accountId);
    }

    public List<HighRiskAccount> listFlagged(int max) {
        return cache.listFlagged(max);
    }

    private RiskStatus loadOnce(String accountId) {
        Optional<String> token = cache.tryAcquireLoadLock(accountId);
        if (token.isPresent()) {
            try {
                return loadAndCache(accountId);
            } finally {
                cache.releaseLoadLock(accountId, token.get());
            }
        }
        // Another instance is loading: give it a short budget, then load ourselves rather than fail.
        long deadline = System.nanoTime() + lockWait.toNanos();
        while (System.nanoTime() < deadline) {
            sleep(pollInterval);
            Optional<RiskStatus> filled = cache.get(accountId);
            if (filled.isPresent()) {
                return filled.get();
            }
        }
        return loadAndCache(accountId);
    }

    private RiskStatus loadAndCache(String accountId) {
        RiskStatus status = source.findActiveFlag(accountId)
                .<RiskStatus>map(RiskStatus.Flagged::new)
                .orElseGet(() -> new RiskStatus.Clear(accountId));
        cache.put(status);
        return status;
    }

    private static RiskStatus join(CompletableFuture<RiskStatus> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : e;
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for another loader", e);
        }
    }
}
