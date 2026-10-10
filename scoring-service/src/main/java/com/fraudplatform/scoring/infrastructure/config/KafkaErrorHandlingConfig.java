package com.fraudplatform.scoring.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.scoring.application.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fraudplatform.scoring.infrastructure.kafka.LoggingRebalanceListener;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.fraudplatform.messaging.backoff.JitteredExponentialBackOff;
import java.time.Duration;

/**
 * What happens when a record fails.
 *
 * <ul>
 *   <li><b>Poison pills</b> ({@link InvalidEventException}): straight to the DLT, no retries.
 *       Retrying a malformed message can never succeed and would block the partition.
 *   <li><b>Transient errors</b> (Redis/Kafka hiccup): 3 retries with exponential backoff and
 *       jitter (200 ms, 400 ms, 800 ms ± 50%), then the DLT, from where it can be replayed.
 * </ul>
 * The DLT record carries the original topic/partition/offset and the exception in headers.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandlingConfig {

    static final String DLT = Topics.TRANSACTIONS_RECEIVED + ".DLT";

    /** Applied by Spring Boot to the listener container factory: log every partition movement. */
    @Bean
    ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>> rebalanceLogging() {
        return container -> container.getContainerProperties().setConsumerRebalanceListener(new LoggingRebalanceListener());
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, MeterRegistry meters) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)); // -1: let Kafka pick the partition
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new JitteredExponentialBackOff(Duration.ofMillis(200), 2.0, 0.5, Duration.ofSeconds(5), 3));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {}

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) { // = sent to the DLT
                meters.counter("kafka_dead_letters_total", "topic", record.topic()).increment();
            }
        });
        return handler;
    }
}
