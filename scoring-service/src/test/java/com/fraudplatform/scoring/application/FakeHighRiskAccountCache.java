package com.fraudplatform.scoring.application;

import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Thread-safe in-memory stand-in for the Redis adapter, for concurrency tests of the use case. */
class FakeHighRiskAccountCache implements HighRiskAccountCache {

    final Map<String, RiskStatus> entries = new ConcurrentHashMap<>();
    final Map<String, String> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<RiskStatus> get(String accountId) {
        return Optional.ofNullable(entries.get(accountId));
    }

    @Override
    public void put(RiskStatus status) {
        entries.put(status.accountId(), status);
    }

    @Override
    public void evict(String accountId) {
        entries.remove(accountId);
    }

    @Override
    public Set<String> flaggedAmong(Collection<String> accountIds) {
        return accountIds.stream().filter(a -> entries.get(a) instanceof RiskStatus.Flagged).collect(Collectors.toSet());
    }

    @Override
    public List<HighRiskAccount> listFlagged(int max) {
        return entries.values().stream()
                .filter(RiskStatus.Flagged.class::isInstance)
                .map(s -> ((RiskStatus.Flagged) s).account())
                .limit(max)
                .toList();
    }

    @Override
    public Optional<String> tryAcquireLoadLock(String accountId) {
        String token = UUID.randomUUID().toString();
        return locks.putIfAbsent(accountId, token) == null ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void releaseLoadLock(String accountId, String token) {
        locks.remove(accountId, token);
    }
}
