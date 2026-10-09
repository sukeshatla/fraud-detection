package com.fraudplatform.scoring.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code fraud.scoring.*}: every rule threshold is tunable per environment. */
@ConfigurationProperties("fraud.scoring")
public record ScoringProperties(
        @DefaultValue(Topics.FRAUD_ALERTS) String alertTopic,
        @DefaultValue("12") int partitions,
        @DefaultValue("1") short replicationFactor,
        @DefaultValue("5s") Duration publishTimeout,
        @DefaultValue("7d") Duration processedTtl,
        @DefaultValue("24h") Duration activityRetention,
        @DefaultValue("64") int maxConcurrentAccounts,
        @DefaultValue Rules rules,
        @DefaultValue RiskCache riskCache,
        @DefaultValue Ml ml) {

    /**
     * @param enabled        false → rules only
     * @param model          model artifact written by ml/train.py
     * @param ruleWeight     w in max(rule, w·rule + (1−w)·100p)
     * @param maxConcurrent  bulkhead permits
     * @param acquireTimeout how long to wait for a permit before falling back
     */
    public record Ml(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("classpath:ml/model-v1.json") String model,
            @DefaultValue("0.6") double ruleWeight,
            @DefaultValue("32") int maxConcurrent,
            @DefaultValue("20ms") Duration acquireTimeout) {}

    /**
     * @param flagWindow how long a DECLINE keeps an account flagged (source-of-truth query)
     * @param ttl        cache TTL for flagged accounts, ± {@code jitter}
     * @param clearTtl   negative-cache TTL for clean accounts
     * @param lockTtl    loader-lock lease
     * @param lockWait   how long a non-leader instance waits for the leader's result
     */
    public record RiskCache(
            @DefaultValue("24h") Duration flagWindow,
            @DefaultValue("1h") Duration ttl,
            @DefaultValue("5m") Duration clearTtl,
            @DefaultValue("0.10") double jitter,
            @DefaultValue("2s") Duration lockTtl,
            @DefaultValue("300ms") Duration lockWait) {}

    public record Rules(
            @DefaultValue("5000") BigDecimal highAmountUsd,
            Map<String, BigDecimal> usdRates,
            @DefaultValue("5") int velocityMaxPerMinute,
            @DefaultValue("1h") Duration geoVelocityWindow,
            @DefaultValue({"7995", "6051", "4829", "6211"}) Set<String> highRiskMccs,
            @DefaultValue("2.00") BigDecimal cardTestingAmount,
            @DefaultValue("3") int cardTestingMinCount) {

        public Rules {
            usdRates = usdRates == null ? Map.of() : Map.copyOf(usdRates);
        }
    }
}
