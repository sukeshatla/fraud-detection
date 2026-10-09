package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.scoring.application.InvalidEventException;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Instant;
import java.util.List;
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
        listener().onTransactions(List.of(valid("t1"), valid("t2")));

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
        assertThatThrownBy(() -> listener().onTransactions(List.of(valid("t1"), "not json", valid("t3"))))
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
        assertThatThrownBy(() -> listener().onTransactions(List.of("{\"schemaVersion\":1}", valid("t2"))))
                .isInstanceOf(BatchListenerFailedException.class);
        verifyNoInteractions(service);
    }
}
