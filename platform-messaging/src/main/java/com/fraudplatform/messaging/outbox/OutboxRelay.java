package com.fraudplatform.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Moves committed outbox rows to Kafka. One call = one batch in one DB transaction:
 *
 * <ol>
 *   <li>{@code pg_try_advisory_xact_lock(lockKey)}: only one relay per service works at a time.
 *       Others return 0 immediately and take over if the leader dies (the lock is released with
 *       its transaction/connection). A single active relay keeps <b>insertion order</b>.
 *   <li>Read the oldest {@code batchSize} rows, send them all, wait for every broker ack.
 *   <li>Delete them and commit.
 * </ol>
 *
 * <p>Any send failure throws, and the transaction rolls back: rows stay and are retried. A crash
 * after the send but before the commit re-sends them, so delivery is <b>at-least-once</b> and
 * consumers must be idempotent (they are: deterministic ids, ON CONFLICT DO NOTHING).
 */
public class OutboxRelay {

    private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaOperations<String, String> kafka;
    private final JsonMapper mapper;
    private final long lockKey;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Counter relayed;
    private final Counter failures;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaOperations<String, String> kafka, JsonMapper mapper,
            long lockKey, int batchSize, Duration sendTimeout, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.mapper = mapper;
        this.lockKey = lockKey;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.relayed = Counter.builder("outbox_relayed_total").register(meters);
        this.failures = Counter.builder("outbox_relay_failures_total").register(meters);
    }

    private record Row(long id, String topic, String key, String payload, String headers) {}

    /** @return number of messages published (0 if idle or another relay holds the lock) */
    public int relayOnce() {
        try {
            Integer sent = tx.execute(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, lockKey))) {
                    return 0; // another instance is the active relay
                }
                List<Row> rows = jdbc.query("""
                        SELECT id, topic, message_key, payload, headers::text AS headers
                        FROM outbox ORDER BY id LIMIT ?""",
                        (rs, i) -> new Row(rs.getLong("id"), rs.getString("topic"), rs.getString("message_key"),
                                rs.getString("payload"), rs.getString("headers")),
                        batchSize);
                if (rows.isEmpty()) {
                    return 0;
                }
                publish(rows);
                jdbc.update("DELETE FROM outbox WHERE id = ANY(?)", (Object) rows.stream().map(Row::id).toArray(Long[]::new));
                return rows.size();
            });
            int count = sent == null ? 0 : sent;
            relayed.increment(count);
            return count;
        } catch (RuntimeException e) {
            failures.increment();
            throw e;
        }
    }

    private void publish(List<Row> rows) {
        List<CompletableFuture<?>> acks = new ArrayList<>(rows.size());
        for (Row row : rows) { // sent in id order on one producer → per-partition order is preserved
            ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
            mapper.readValue(row.headers(), HEADERS)
                    .forEach((name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
            acks.add(kafka.send(record));
        }
        try {
            CompletableFuture.allOf(acks.toArray(CompletableFuture[]::new)).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Kafka rejected outbox batch; it will be retried", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Kafka did not acknowledge outbox batch within " + sendTimeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while relaying outbox", e);
        }
    }
}
