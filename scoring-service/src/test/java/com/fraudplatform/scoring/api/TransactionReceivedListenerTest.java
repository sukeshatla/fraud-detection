package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.scoring.application.InvalidEventException;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TransactionReceivedListenerTest {

    private static final String VALID = """
            {"schemaVersion":1,"eventId":"0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11","transactionId":"txn-1",
             "accountId":"acc-1","amount":249.99,"currency":"USD","merchantId":"m-1","merchantCategoryCode":"5732",
             "country":"US","channel":"CARD_NOT_PRESENT","occurredAt":"2026-10-09T18:15:30Z",
             "receivedAt":"2026-10-09T18:15:30.120Z"}
            """;

    @Mock
    private ScoreTransactionService service;

    private TransactionReceivedListener listener() {
        return new TransactionReceivedListener(service, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("AC-003-01: event JSON is mapped to the scoring domain transaction")
    void mapsEventToDomain() {
        listener().onTransaction(VALID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(service).score(captor.capture());
        Transaction tx = captor.getValue();
        assertThat(tx.eventId()).isEqualTo(UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11"));
        assertThat(tx.accountId()).isEqualTo("acc-1");
        assertThat(tx.amount()).isEqualByComparingTo("249.99");
        assertThat(tx.merchantCategoryCode()).isEqualTo("5732");
        assertThat(tx.occurredAt()).isEqualTo(Instant.parse("2026-10-09T18:15:30Z"));
    }

    @Test
    @DisplayName("AC-003-09: unparseable payload → InvalidEventException (non-retryable → DLT)")
    void rejectsGarbage() {
        assertThatThrownBy(() -> listener().onTransaction("not json")).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-003-09: structurally valid JSON missing required fields → InvalidEventException")
    void rejectsIncompleteEvent() {
        assertThatThrownBy(() -> listener().onTransaction("{\"schemaVersion\":1}"))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(service);
    }
}
