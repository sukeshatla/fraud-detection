package com.fraudplatform.scoring.api;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.TransactionReceivedEvent;
import com.fraudplatform.scoring.application.InvalidEventException;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.Transaction;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inbound adapter: Kafka → use case.
 *
 * <p>One listener thread per assigned partition ({@code spring.kafka.listener.concurrency}), so the
 * records of one account (one partition) are processed strictly in order.
 */
@Component
class TransactionReceivedListener {

    private final ScoreTransactionService service;
    private final JsonMapper mapper;

    TransactionReceivedListener(ScoreTransactionService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @KafkaListener(id = "scoring", topics = Topics.TRANSACTIONS_RECEIVED, groupId = "scoring")
    void onTransaction(String payload) {
        service.score(toDomain(payload));
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
