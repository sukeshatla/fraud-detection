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
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens when a record fails.
 *
 * <ul>
 *   <li><b>Poison pills</b> ({@link InvalidEventException}): straight to the DLT, no retries.
 *       Retrying a malformed message can never succeed and would block the partition.
 *   <li><b>Transient errors</b> (Redis/Kafka hiccup): a couple of quick retries, then the DLT.
 *       Feature 010 upgrades this to exponential backoff with jitter.
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
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
