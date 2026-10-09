package com.fraudplatform.alerts.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param lockTtl        lease for the per-alert Redis lock (≫ p99 of a review request)
 * @param publishTimeout broker ack timeout for resolution events
 */
@ConfigurationProperties("fraud.alerts")
public record AlertProperties(
        @DefaultValue("5s") Duration lockTtl,
        @DefaultValue("5s") Duration publishTimeout,
        @DefaultValue("1") short replicationFactor) {}
