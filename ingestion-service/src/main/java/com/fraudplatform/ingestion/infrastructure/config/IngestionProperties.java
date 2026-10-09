package com.fraudplatform.ingestion.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed configuration under {@code fraud.ingestion.*}.
 *
 * @param topic             destination topic
 * @param partitions        partition count when the topic is auto-created (upper bound on consumer parallelism)
 * @param replicationFactor replicas per partition (3 in production, 1 locally)
 * @param publishTimeout    how long a request waits for the broker ack before answering 503
 * @param maxClockSkew      how far in the future {@code occurredAt} may be
 */
@ConfigurationProperties("fraud.ingestion")
public record IngestionProperties(
        @DefaultValue(Topics.TRANSACTIONS_RECEIVED) String topic,
        @DefaultValue("12") int partitions,
        @DefaultValue("1") short replicationFactor,
        @DefaultValue("5s") Duration publishTimeout,
        @DefaultValue("5m") Duration maxClockSkew) {}
