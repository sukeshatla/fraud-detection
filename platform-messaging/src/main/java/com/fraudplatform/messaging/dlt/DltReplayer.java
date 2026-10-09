package com.fraudplatform.messaging.dlt;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;

/**
 * Re-publishes dead letters to their original topic once the cause is fixed (a dependency is back,
 * a bug is deployed). Keeps the key (so partition and order are preserved), strips the
 * {@code kafka_dlt-*} diagnostic headers, and commits a dedicated consumer group's offsets only
 * after the re-publish is acknowledged, so every dead letter is replayed at most once per group.
 */
public class DltReplayer {

    private static final Duration POLL = Duration.ofMillis(500);
    private static final int EMPTY_POLLS_BEFORE_DONE = 3;

    private final ConsumerFactory<String, String> consumers;
    private final KafkaOperations<String, String> kafka;
    private final String groupId;

    public DltReplayer(ConsumerFactory<String, String> consumers, KafkaOperations<String, String> kafka, String groupId) {
        this.consumers = consumers;
        this.kafka = kafka;
        this.groupId = groupId;
    }

    /** @return number of records replayed (≤ max) */
    public synchronized int replay(String dltTopic, String targetTopic, int max) {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        overrides.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.max(1, max));
        Map<TopicPartition, OffsetAndMetadata> done = new HashMap<>();
        int replayed = 0;
        try (Consumer<String, String> consumer = consumers.createConsumer(groupId, null, null, overrides)) {
            consumer.subscribe(List.of(dltTopic));
            int emptyPolls = 0;
            while (replayed < max && emptyPolls < EMPTY_POLLS_BEFORE_DONE) {
                var records = consumer.poll(POLL);
                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }
                for (ConsumerRecord<String, String> record : records) {
                    if (replayed == max) {
                        break; // uncommitted: picked up by the next replay
                    }
                    republish(record, targetTopic);
                    done.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset() + 1));
                    replayed++;
                }
            }
            if (!done.isEmpty()) {
                consumer.commitSync(done);
            }
        }
        return replayed;
    }

    private void republish(ConsumerRecord<String, String> dead, String targetTopic) {
        ProducerRecord<String, String> retry = new ProducerRecord<>(targetTopic, dead.key(), dead.value());
        for (Header header : dead.headers()) {
            if (!header.key().startsWith("kafka_dlt")) {
                retry.headers().add(header);
            }
        }
        CompletableFuture<?> ack = kafka.send(retry);
        try {
            ack.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during DLT replay", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not re-publish dead letter at offset " + dead.offset(), e);
        }
    }
}
