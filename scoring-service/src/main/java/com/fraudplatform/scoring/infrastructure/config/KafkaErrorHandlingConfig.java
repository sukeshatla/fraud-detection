package com.fraudplatform.scoring.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.scoring.application.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
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

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)); // -1: let Kafka pick the partition
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new JitteredExponentialBackOff(Duration.ofMillis(200), 2.0, 0.5, Duration.ofSeconds(5), 3));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
