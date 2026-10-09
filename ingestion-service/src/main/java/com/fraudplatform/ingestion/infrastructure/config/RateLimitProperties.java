package com.fraudplatform.ingestion.infrastructure.config;

import com.fraudplatform.ingestion.infrastructure.redis.Quota;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code fraud.ingestion.rate-limit.*} and {@code fraud.ingestion.idempotency.*}.
 *
 * @param enabled      turn the limiter off entirely (e.g. for load-test baselines)
 * @param defaultQuota quota for clients without an explicit entry
 * @param clients      per-client overrides keyed by client id (contracted tiers)
 */
@ConfigurationProperties("fraud.ingestion.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Quota defaultQuota,
        Map<String, Quota> clients) {

    public RateLimitProperties {
        if (defaultQuota == null) {
            defaultQuota = new Quota(200, 100);
        }
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    public Quota quotaFor(String clientId) {
        return clients.getOrDefault(clientId, defaultQuota);
    }

    /** Idempotency-key retention. */
    @ConfigurationProperties("fraud.ingestion.idempotency")
    public record Idempotency(@DefaultValue("24h") Duration ttl) {}
}
