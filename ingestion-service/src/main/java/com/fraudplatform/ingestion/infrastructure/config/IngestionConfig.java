package com.fraudplatform.ingestion.infrastructure.config;

import com.fraudplatform.ingestion.application.IdempotencyStore;
import com.fraudplatform.ingestion.application.IngestTransactionService;
import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.RateLimiter;
import com.fraudplatform.ingestion.application.TransactionPublisher;
import com.fraudplatform.ingestion.infrastructure.kafka.KafkaTransactionPublisher;
import com.fraudplatform.ingestion.infrastructure.redis.RedisIdempotencyStore;
import com.fraudplatform.ingestion.infrastructure.redis.RedisTokenBucketRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Composition root: wires the framework-free use case to its adapters. */
@Configuration(proxyBeanMethods = false)
class IngestionConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionPublisher transactionPublisher(
            KafkaTemplate<String, String> template, JsonMapper mapper, IngestionProperties props) {
        return new KafkaTransactionPublisher(template, mapper, props.topic(), props.publishTimeout());
    }

    @Bean
    IdempotencyStore idempotencyStore(
            StringRedisTemplate redis, JsonMapper mapper, RateLimitProperties.Idempotency props, MeterRegistry meters) {
        return new RedisIdempotencyStore(redis, mapper, props.ttl(), meters);
    }

    @Bean
    RateLimiter rateLimiter(StringRedisTemplate redis, RateLimitProperties props, MeterRegistry meters) {
        if (!props.enabled()) {
            return clientId -> RateLimitDecision.allowed(Long.MAX_VALUE, Long.MAX_VALUE);
        }
        return new RedisTokenBucketRateLimiter(redis, props::quotaFor, meters);
    }

    @Bean
    IngestTransactionService ingestTransactionService(
            TransactionPublisher publisher, IdempotencyStore idempotency, Clock clock, IngestionProperties props) {
        return new IngestTransactionService(publisher, idempotency, clock, UUID::randomUUID, props.maxClockSkew());
    }

    /** Declared so local/dev environments get the right partition count. In production, topics are managed by IaC. */
    @Bean
    NewTopic transactionsReceivedTopic(IngestionProperties props) {
        return TopicBuilder.name(props.topic())
                .partitions(props.partitions())
                .replicas(props.replicationFactor())
                .build();
    }
}
