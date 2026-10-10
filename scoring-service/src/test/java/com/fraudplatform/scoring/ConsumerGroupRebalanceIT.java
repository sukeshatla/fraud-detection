package com.fraudplatform.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fraudplatform.scoring.support.IntegrationTest;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

/**
 * AC-011-06: Kafka's own load balancing. Partitions are spread across a consumer group; when a
 * member leaves, its partitions move to the survivors. With the cooperative-sticky assignor the
 * survivors KEEP what they had (incremental rebalance: no stop-the-world revoke of everything).
 */
@IntegrationTest
class ConsumerGroupRebalanceIT {

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("AC-011-06: 3 consumers × 12 partitions → 4 each; one dies → survivors get 6 each and keep their own 4")
    void cooperativeStickyRebalance() throws Exception {
        String topic = "rebalance-" + UUID.randomUUID();
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 12, (short) 1))).all().get();
        }
        String group = "scoring-like-" + UUID.randomUUID();
        KafkaConsumer<String, String> a = consumer(group, topic);
        KafkaConsumer<String, String> b = consumer(group, topic);
        KafkaConsumer<String, String> c = consumer(group, topic);
        try {
            pollUntil(List.of(a, b, c), () -> a.assignment().size() == 4 && b.assignment().size() == 4 && c.assignment().size() == 4);
            Set<TopicPartition> aBefore = new HashSet<>(a.assignment());
            Set<TopicPartition> bBefore = new HashSet<>(b.assignment());
            Set<TopicPartition> all = new HashSet<>(aBefore);
            all.addAll(bBefore);
            all.addAll(c.assignment());
            assertThat(all).hasSize(12); // disjoint and complete

            c.close(); // an instance is killed / scaled down

            pollUntil(List.of(a, b), () -> a.assignment().size() == 6 && b.assignment().size() == 6);
            assertThat(a.assignment()).containsAll(aBefore); // sticky: nothing taken away from survivors
            assertThat(b.assignment()).containsAll(bBefore);
        } finally {
            a.close();
            b.close();
        }
    }

    /** KafkaConsumer is single-threaded: drive all members from this thread until the group settles. */
    private static void pollUntil(List<KafkaConsumer<String, String>> consumers, java.util.concurrent.Callable<Boolean> settled) {
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            consumers.forEach(consumer -> consumer.poll(Duration.ofMillis(100)));
            return settled.call();
        });
    }

    private KafkaConsumer<String, String> consumer(String group, String topic) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, group,
                ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
