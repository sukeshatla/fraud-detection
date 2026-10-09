package com.fraudplatform.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.KafkaTestConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** AC-010-04: the outbox against real PostgreSQL and Kafka. */
@Testcontainers
class OutboxIT {

    @Container
    static final PostgreSQLContainer POSTGRES = Containers.postgres();

    @Container
    static final KafkaContainer KAFKA = Containers.kafka();

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static KafkaTemplate<String, String> kafka;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private OutboxWriter writer;
    private String topic;

    @BeforeAll
    static void infrastructure() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("com/fraudplatform/messaging/outbox/outbox.sql"));
        }
        kafka = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)));
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox");
        writer = new OutboxWriter(jdbc, MAPPER);
        topic = "outbox-it-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("AC-010-04: committed message is relayed with key, payload and headers; the outbox is emptied")
    void relaysCommittedMessages() {
        tx.executeWithoutResult(s -> writer.append(List.of(new OutboxMessage(topic, "acc-1", "{\"n\":1}", Map.of("event-type", "Test")))));

        assertThat(relay(kafka).relayOnce()).isEqualTo(1);

        ConsumerRecord<String, String> record = KafkaTestConsumer.awaitRecords(KAFKA.getBootstrapServers(), topic, "acc-1", 1).getFirst();
        assertThat(record.value()).isEqualTo("{\"n\":1}");
        assertThat(new String(record.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8)).isEqualTo("Test");
        assertThat(pending()).isZero();
    }

    @Test
    @DisplayName("AC-010-04: a rolled-back business transaction publishes nothing")
    void rollbackPublishesNothing() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            writer.append(List.of(new OutboxMessage(topic, "acc-1", "{}", Map.of())));
            throw new IllegalStateException("business rule failed after the write");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(pending()).isZero();
        assertThat(relay(kafka).relayOnce()).isZero();
    }

    @Test
    @DisplayName("AC-010-04: appending outside a transaction is refused (it would defeat the point)")
    void requiresTransaction() {
        assertThatThrownBy(() -> writer.append(List.of(new OutboxMessage(topic, "k", "{}", Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");
    }

    @Test
    @DisplayName("AC-010-04: Kafka down after the DB commit → nothing lost; delivered in order once Kafka is back")
    void survivesKafkaOutage() {
        tx.executeWithoutResult(s -> writer.append(List.of(
                new OutboxMessage(topic, "acc-9", "first", Map.of()),
                new OutboxMessage(topic, "acc-9", "second", Map.of()))));

        @SuppressWarnings("unchecked")
        KafkaOperations<String, String> brokerDown = mock(KafkaOperations.class);
        when(brokerDown.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        assertThatThrownBy(() -> relay(brokerDown).relayOnce()).isInstanceOf(IllegalStateException.class);
        assertThat(pending()).as("rows survive the failed relay").isEqualTo(2);

        assertThat(relay(kafka).relayOnce()).isEqualTo(2); // broker is back
        assertThat(KafkaTestConsumer.awaitRecords(KAFKA.getBootstrapServers(), topic, "acc-9", 2))
                .extracting(ConsumerRecord::value).containsExactly("first", "second");
        assertThat(pending()).isZero();
    }

    @Test
    @DisplayName("AC-010-04: two relays racing → the advisory lock lets one work at a time; every message exactly once")
    void twoRelaysNoDuplicates() throws Exception {
        for (int i = 0; i < 200; i++) {
            int n = i;
            tx.executeWithoutResult(s -> writer.append(List.of(new OutboxMessage(topic, "acc-r", "m" + n, Map.of()))));
        }
        OutboxRelay a = relay(kafka);
        OutboxRelay b = relay(kafka);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> loops = List.of(pool.submit(() -> drain(a)), pool.submit(() -> drain(b)));
            assertThat(loops.get(0).get() + loops.get(1).get()).isEqualTo(200);
        }

        List<String> values = KafkaTestConsumer.recordsWithin(KAFKA.getBootstrapServers(), topic, "acc-r", Duration.ofSeconds(3))
                .stream().map(ConsumerRecord::value).toList();
        assertThat(values).hasSize(200).doesNotHaveDuplicates();
        assertThat(values).isEqualTo(java.util.stream.IntStream.range(0, 200).mapToObj(i -> "m" + i).toList()); // order kept
    }

    private static int drain(OutboxRelay relay) {
        int total = 0;
        for (int i = 0; i < 50; i++) {
            total += relay.relayOnce();
        }
        return total;
    }

    private static OutboxRelay relay(KafkaOperations<String, String> kafkaOps) {
        return new OutboxRelay(jdbc, tx, kafkaOps, MAPPER, 42L, 50, Duration.ofSeconds(5), new SimpleMeterRegistry());
    }

    private static int pending() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class);
    }
}
