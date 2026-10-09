package com.fraudplatform.messaging.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.KafkaTestConsumer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** AC-010-03: dead letters can be re-published after the underlying problem is fixed, each exactly once. */
@Testcontainers
class DltReplayerIT {

    @Container
    static final KafkaContainer KAFKA = Containers.kafka();

    @Test
    @DisplayName("AC-010-03: replays up to max, keeps keys, strips DLT headers, and never replays the same record twice")
    void replaysOnceWithLimit() throws Exception {
        String topic = "orders-" + UUID.randomUUID();
        String dlt = topic + ".DLT";
        KafkaTemplate<String, String> kafka = template();
        for (int i = 0; i < 3; i++) {
            ProducerRecord<String, String> dead = new ProducerRecord<>(dlt, "acc-" + i, "payload-" + i);
            dead.headers().add("kafka_dlt-exception-message", "Redis timeout".getBytes(StandardCharsets.UTF_8));
            dead.headers().add("event-type", "TransactionReceived".getBytes(StandardCharsets.UTF_8));
            kafka.send(dead).get();
        }
        DltReplayer replayer = new DltReplayer(consumers(), kafka, "dlt-replay-test");

        assertThat(replayer.replay(dlt, topic, 2)).isEqualTo(2);
        assertThat(replayer.replay(dlt, topic, 100)).isEqualTo(1);
        assertThat(replayer.replay(dlt, topic, 100)).isZero();

        ConsumerRecord<String, String> replayed = KafkaTestConsumer.awaitRecords(KAFKA.getBootstrapServers(), topic, "acc-0", 1).getFirst();
        assertThat(replayed.value()).isEqualTo("payload-0");
        assertThat(replayed.headers().lastHeader("kafka_dlt-exception-message")).isNull();
        assertThat(replayed.headers().lastHeader("event-type")).isNotNull();
        assertThat(KafkaTestConsumer.recordsWithin(KAFKA.getBootstrapServers(), topic, "acc-1", Duration.ofSeconds(2))).hasSize(1);
    }

    private static KafkaTemplate<String, String> template() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    private static DefaultKafkaConsumerFactory<String, String> consumers() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
