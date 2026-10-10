package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.scoring.application.InvalidEventException;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.BatchListenerFailedException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TransactionReceivedListenerTest {

    private static String valid(String txnId) {
        return """
                {"schemaVersion":1,"eventId":"%s","transactionId":"%s",
                 "accountId":"acc-1","amount":249.99,"currency":"USD","merchantId":"m-1","merchantCategoryCode":"5732",
                 "country":"US","channel":"CARD_NOT_PRESENT","occurredAt":"2026-10-09T18:15:30Z",
                 "receivedAt":"2026-10-09T18:15:30.120Z"}
                """.formatted(UUID.randomUUID(), txnId);
    }

    @Mock
    private ScoreTransactionService service;

    private TransactionReceivedListener listener() {
        return new TransactionReceivedListener(service, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("AC-004-02: a whole poll is handed to the use case as one batch, mapped to the domain")
    void mapsBatchToDomain() {
        listener().onTransactions(records(valid("t1"), valid("t2")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).scoreBatch(captor.capture());
        assertThat(captor.getValue()).extracting(Transaction::transactionId).containsExactly("t1", "t2");
        Transaction tx = captor.getValue().getFirst();
        assertThat(tx.amount()).isEqualByComparingTo("249.99");
        assertThat(tx.occurredAt()).isEqualTo(Instant.parse("2026-10-09T18:15:30Z"));
    }

    @Test
    @DisplayName("AC-003-09: poison pill at index 1 → records before it are scored, then BatchListenerFailedException(1)")
    void poisonPillMidBatch() {
        assertThatThrownBy(() -> listener().onTransactions(records(valid("t1"), "not json", valid("t3"))))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, e -> {
                    assertThat(e.getIndex()).isEqualTo(1);
                    assertThat(e.getCause()).isInstanceOf(InvalidEventException.class);
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).scoreBatch(captor.capture());
        assertThat(captor.getValue()).extracting(Transaction::transactionId).containsExactly("t1");
    }

    @Test
    @DisplayName("AC-003-09: poison pill first → nothing scored")
    void poisonPillFirst() {
        assertThatThrownBy(() -> listener().onTransactions(records("{\"schemaVersion\":1}", valid("t2"))))
                .isInstanceOf(BatchListenerFailedException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-012-04/05: traceparent, tracestate and x-request-id headers travel with the transaction as metadata")
    void carriesPropagationHeaders() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("t", 0, 0, "acc-1", valid("t1"));
        record.headers().add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01".getBytes(StandardCharsets.UTF_8));
        record.headers().add("x-request-id", "rid-42".getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-type", "TransactionReceived".getBytes(StandardCharsets.UTF_8)); // not propagated

        listener().onTransactions(List.of(record));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).scoreBatch(captor.capture());
        assertThat(captor.getValue().getFirst().metadata()).containsOnly(
                Map.entry("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"),
                Map.entry("x-request-id", "rid-42"));
    }

    private static List<ConsumerRecord<String, String>> records(String... payloads) {
        List<ConsumerRecord<String, String>> records = new java.util.ArrayList<>();
        for (int i = 0; i < payloads.length; i++) {
            records.add(new ConsumerRecord<>("transactions.received.v1", 0, i, "acc-1", payloads[i]));
        }
        return records;
    }
}
