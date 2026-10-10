package com.fraudplatform.alerts.infrastructure.config;

import com.fraudplatform.alerts.application.AlertChangeBus;
import com.fraudplatform.alerts.application.AlertLock;
import com.fraudplatform.alerts.application.AlertMetrics;
import com.fraudplatform.alerts.application.AlertQueryService;
import com.fraudplatform.alerts.application.AlertRepository;
import com.fraudplatform.alerts.application.IngestAlertService;
import com.fraudplatform.alerts.application.InvalidEventException;
import com.fraudplatform.alerts.application.ReviewAlertService;
import com.fraudplatform.alerts.infrastructure.kafka.AlertResolvedEvents;
import com.fraudplatform.alerts.infrastructure.kafka.CurrentPropagationHeaders;
import com.fraudplatform.alerts.infrastructure.metrics.MicrometerAlertMetrics;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.kafka.core.ProducerFactory;
import com.fraudplatform.messaging.backoff.JitteredExponentialBackOff;
import com.fraudplatform.messaging.outbox.OutboxRelay;
import com.fraudplatform.messaging.outbox.OutboxRelayRunner;
import com.fraudplatform.messaging.outbox.OutboxWriter;
import java.time.Duration;
import com.fraudplatform.alerts.infrastructure.persistence.JpaAlertRepository;
import com.fraudplatform.alerts.infrastructure.redis.RedisAlertChangeBus;
import com.fraudplatform.alerts.infrastructure.redis.RedisAlertLock;
import com.fraudplatform.contracts.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import io.micrometer.observation.ObservationPredicate;
import java.time.Clock;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Composition root for alert-service. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling // SSE heartbeats
class AlertConfig {

    /** pg_advisory_lock key electing the single active outbox relay among alert-service instances. */
    static final long OUTBOX_LOCK_KEY = 0xA1E_0001L;
    static final int OUTBOX_BATCH = 500;

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Don't trace health checks / scrapes: every few seconds per replica, they'd drown real traces. */
    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    @Bean
    AlertRepository alertRepository(EntityManager em, JdbcTemplate jdbc, TransactionTemplate tx, OutboxWriter outbox,
            JsonMapper mapper, Tracer tracer, Propagator propagator) {
        return new JpaAlertRepository(em, jdbc, tx, outbox,
                new AlertResolvedEvents(mapper, new CurrentPropagationHeaders(tracer, propagator)));
    }

    @Bean
    AlertMetrics alertMetrics(MeterRegistry meters) {
        return new MicrometerAlertMetrics(meters);
    }

    @Bean
    AlertLock alertLock(StringRedisTemplate redis, AlertProperties props) {
        return new RedisAlertLock(redis, props.lockTtl());
    }

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, JsonMapper mapper) {
        return new OutboxWriter(jdbc, mapper);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, ProducerFactory<String, String> producers,
            JsonMapper mapper, AlertProperties props, MeterRegistry meters) {
        // Non-observed template: outbox rows already carry the trace context of the request that wrote them.
        return new OutboxRelay(jdbc, tx, new KafkaTemplate<>(producers), mapper, OUTBOX_LOCK_KEY, OUTBOX_BATCH,
                props.publishTimeout(), meters);
    }

    @Bean
    OutboxRelayRunner outboxRelayRunner(OutboxRelay relay) {
        return new OutboxRelayRunner(relay, OUTBOX_BATCH, Duration.ofMillis(100), Duration.ofSeconds(1));
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    AlertChangeBus alertChangeBus(StringRedisTemplate redis, RedisMessageListenerContainer container, JsonMapper mapper) {
        return new RedisAlertChangeBus(redis, container, mapper);
    }

    @Bean
    IngestAlertService ingestAlertService(AlertRepository repository, AlertChangeBus changes, AlertMetrics metrics) {
        return new IngestAlertService(repository, changes, metrics);
    }

    @Bean
    AlertQueryService alertQueryService(AlertRepository repository, Clock clock) {
        return new AlertQueryService(repository, clock);
    }

    @Bean
    ReviewAlertService reviewAlertService(AlertRepository repository, AlertLock lock, AlertChangeBus changes,
            AlertMetrics metrics, Clock clock) {
        return new ReviewAlertService(repository, lock, changes, metrics, clock);
    }

    /** Poison pills → DLT immediately; transient failures retry briefly first. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, MeterRegistry meters) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template, (r, e) -> new TopicPartition(r.topic() + ".DLT", -1)),
                new JitteredExponentialBackOff(Duration.ofMillis(200), 2.0, 0.5, Duration.ofSeconds(5), 3));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {}

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                meters.counter("kafka_dead_letters_total", "topic", record.topic()).increment();
            }
        });
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
