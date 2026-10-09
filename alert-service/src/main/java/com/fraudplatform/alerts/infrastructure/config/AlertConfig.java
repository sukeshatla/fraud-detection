package com.fraudplatform.alerts.infrastructure.config;

import com.fraudplatform.alerts.application.AlertLock;
import com.fraudplatform.alerts.application.AlertQueryService;
import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.alerts.application.AlertResolutionPublisher;
import com.fraudplatform.alerts.application.IngestAlertService;
import com.fraudplatform.alerts.application.InvalidEventException;
import com.fraudplatform.alerts.application.ReviewAlertService;
import com.fraudplatform.alerts.infrastructure.kafka.KafkaAlertResolutionPublisher;
import com.fraudplatform.alerts.infrastructure.persistence.JpaAlertRepository;
import com.fraudplatform.alerts.infrastructure.redis.RedisAlertLock;
import com.fraudplatform.contracts.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

/** Composition root for alert-service. */
@Configuration(proxyBeanMethods = false)
class AlertConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AlertRepository alertRepository(EntityManager em, JdbcTemplate jdbc, TransactionTemplate tx) {
        return new JpaAlertRepository(em, jdbc, tx);
    }

    @Bean
    AlertLock alertLock(StringRedisTemplate redis, AlertProperties props) {
        return new RedisAlertLock(redis, props.lockTtl());
    }

    @Bean
    AlertResolutionPublisher alertResolutionPublisher(KafkaTemplate<String, String> template, JsonMapper mapper,
            AlertProperties props, MeterRegistry meters) {
        return new KafkaAlertResolutionPublisher(template, mapper, props.publishTimeout(), meters);
    }

    @Bean
    IngestAlertService ingestAlertService(AlertRepository repository) {
        return new IngestAlertService(repository);
    }

    @Bean
    AlertQueryService alertQueryService(AlertRepository repository) {
        return new AlertQueryService(repository);
    }

    @Bean
    ReviewAlertService reviewAlertService(AlertRepository repository, AlertLock lock, AlertResolutionPublisher publisher,
            Clock clock) {
        return new ReviewAlertService(repository, lock, publisher, clock);
    }

    /** Poison pills → DLT immediately; transient failures retry briefly first. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template, (r, e) -> new TopicPartition(r.topic() + ".DLT", -1)),
                new FixedBackOff(500L, 2L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    @Bean
    NewTopic fraudAlertsTopic(AlertProperties props) {
        return TopicBuilder.name(Topics.FRAUD_ALERTS).partitions(6).replicas(props.replicationFactor()).build();
    }

    @Bean
    NewTopic fraudAlertsDlt(AlertProperties props) {
        return TopicBuilder.name(Topics.FRAUD_ALERTS + ".DLT").partitions(1).replicas(props.replicationFactor()).build();
    }

    @Bean
    NewTopic alertResolutionsTopic(AlertProperties props) {
        return TopicBuilder.name(Topics.ALERT_RESOLUTIONS).partitions(6).replicas(props.replicationFactor()).build();
    }
}
