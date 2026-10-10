package com.fraudplatform.scoring.api;

import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.scoring.application.InvalidEventException;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.Transaction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound adapter: one Kafka poll → one use-case call (JDBC batching needs the whole batch).
 *
 * <p><b>Poison pills in a batch.</b> Records before the first invalid one are scored. Then
 * {@link BatchListenerFailedException} tells the error handler the exact index: offsets before it
 * are committed, that record goes to the DLT, and the rest are redelivered.
 *
 * <p>One listener thread per assigned partition, so one account's records stay in order.
 */
@Component
class TransactionReceivedListener {

    static final List<String> PROPAGATED = List.of(EventHeaders.TRACEPARENT, EventHeaders.TRACESTATE, EventHeaders.REQUEST_ID);

    private final ScoreTransactionService service;
    private final JsonMapper mapper;

    TransactionReceivedListener(ScoreTransactionService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @KafkaListener(id = "scoring", topics = Topics.TRANSACTIONS_RECEIVED, groupId = "scoring", batch = "true")
    void onTransactions(List<ConsumerRecord<String, String>> records) {
        List<Transaction> valid = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            try {
                ConsumerRecord<String, String> record = records.get(i);
                valid.add(toDomain(record.value()).withMetadata(propagationHeaders(record)));
            } catch (InvalidEventException e) {
                if (!valid.isEmpty()) {
                    service.scoreBatch(valid);
                }
                throw new BatchListenerFailedException("Invalid record in batch", e, i);
            }
        }
        service.scoreBatch(valid);
    }

    /**
     * Batch processing has no single "current span", so the trace context and request id of each
     * record are carried with its transaction and copied onto any alert it causes. The trace then
     * continues downstream even across the outbox.
     */
    private static Map<String, String> propagationHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> metadata = new HashMap<>();
        for (String name : PROPAGATED) {
            Header header = record.headers().lastHeader(name);
            if (header != null) {
                metadata.put(name, new String(header.value(), StandardCharsets.UTF_8));
            }
        }
        return metadata;
    }

    private Transaction toDomain(String payload) {
        try {
            TransactionReceivedEvent e = mapper.readValue(payload, TransactionReceivedEvent.class);
            return new Transaction(e.eventId(), e.transactionId(), e.accountId(), e.amount(), e.currency(),
                    e.merchantId(), e.merchantCategoryCode(), e.country(), e.channel(), e.occurredAt());
        } catch (JacksonException | NullPointerException e) {
            throw new InvalidEventException("Unprocessable TransactionReceivedEvent", e);
        }
    }
}
