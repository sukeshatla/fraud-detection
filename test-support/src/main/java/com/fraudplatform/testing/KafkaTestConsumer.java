package com.fraudplatform.testing;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Reads a topic from the beginning with a throw-away consumer group, filtering by record key. */
public final class KafkaTestConsumer {

    private KafkaTestConsumer() {}

    /** Waits (up to 30 s) until {@code expected} records with {@code key} have arrived. */
    public static List<ConsumerRecord<String, String>> awaitRecords(
            String bootstrapServers, String topic, String key, int expected) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer(bootstrapServers, topic)) {
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                poll(consumer, key, matching);
                return matching.size() >= expected;
            });
        }
        return matching;
    }

    /** Collects every record with {@code key} seen within {@code window}: proves something did NOT happen twice. */
    public static List<ConsumerRecord<String, String>> recordsWithin(
            String bootstrapServers, String topic, String key, Duration window) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer(bootstrapServers, topic)) {
            Instant deadline = Instant.now().plus(window);
            while (Instant.now().isBefore(deadline)) {
                poll(consumer, key, matching);
            }
        }
        return matching;
    }

    private static void poll(KafkaConsumer<String, String> consumer, String key, List<ConsumerRecord<String, String>> sink) {
        consumer.poll(Duration.ofMillis(250)).forEach(r -> {
            if (key.equals(r.key())) {
                sink.add(r);
            }
        });
    }

    private static KafkaConsumer<String, String> consumer(String bootstrapServers, String topic) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
