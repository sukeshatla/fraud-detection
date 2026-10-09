package com.fraudplatform.ingestion.application;

import static com.fraudplatform.ingestion.domain.TransactionFixtures.aTransactionOccurredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.ingestion.domain.ReceivedTransaction;
import com.fraudplatform.ingestion.domain.Transaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T18:15:30.120Z");
    private static final UUID EVENT_ID = UUID.fromString("0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11");
    private static final Duration MAX_SKEW = Duration.ofMinutes(5);

    @Mock
    private TransactionPublisher publisher;

    private IngestTransactionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new IngestTransactionService(publisher, clock, () -> EVENT_ID, MAX_SKEW);
    }

    @Test
    @DisplayName("AC-001-01: accepted transaction gets an eventId and receivedAt from the clock")
    void returnsReceiptForAcceptedTransaction() {
        Transaction tx = aTransactionOccurredAt(NOW.minusSeconds(2));

        IngestionReceipt receipt = service.ingest(tx);

        assertThat(receipt.transactionId()).isEqualTo(tx.transactionId());
        assertThat(receipt.eventId()).isEqualTo(EVENT_ID);
        assertThat(receipt.receivedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-001-02: accepted transaction is handed to the publisher port")
    void publishesReceivedTransaction() {
        Transaction tx = aTransactionOccurredAt(NOW.minusSeconds(2));

        service.ingest(tx);

        ArgumentCaptor<ReceivedTransaction> captor = ArgumentCaptor.forClass(ReceivedTransaction.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new ReceivedTransaction(EVENT_ID, NOW, tx));
    }

    @Test
    @DisplayName("AC-001-05: occurredAt more than 5 min in the future is rejected and not published")
    void rejectsTransactionsTooFarInTheFuture() {
        Transaction tx = aTransactionOccurredAt(NOW.plus(MAX_SKEW).plusSeconds(1));

        assertThatThrownBy(() -> service.ingest(tx))
                .isInstanceOf(TransactionRejectedException.class)
                .hasMessageContaining("occurredAt");
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("AC-001-05: occurredAt within clock-skew tolerance is accepted")
    void acceptsTransactionsWithinSkewTolerance() {
        Transaction tx = aTransactionOccurredAt(NOW.plus(MAX_SKEW));

        assertThat(service.ingest(tx).eventId()).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("AC-001-06: publishing failure propagates so the caller never sees success")
    void propagatesPublishingFailure() {
        doThrow(new EventPublishingException("broker unavailable", null)).when(publisher).publish(any());

        assertThatThrownBy(() -> service.ingest(aTransactionOccurredAt(NOW)))
                .isInstanceOf(EventPublishingException.class);
    }
}
