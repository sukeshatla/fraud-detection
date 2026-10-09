package com.fraudplatform.ingestion.application;

import static com.fraudplatform.ingestion.domain.TransactionFixtures.aTransactionOccurredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.ingestion.domain.ReceivedTransaction;
import com.fraudplatform.ingestion.domain.Transaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    private static final String KEY = "idem-123";

    @Mock
    private TransactionPublisher publisher;

    @Mock
    private IdempotencyStore idempotency;

    private IngestTransactionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new IngestTransactionService(publisher, idempotency, clock, () -> EVENT_ID, MAX_SKEW);
    }

    @Nested
    class WithoutIdempotencyKey {

        @Test
        @DisplayName("AC-001-01: accepted transaction gets an eventId and receivedAt from the clock")
        void returnsReceiptForAcceptedTransaction() {
            Transaction tx = aTransactionOccurredAt(NOW.minusSeconds(2));

            IngestionReceipt receipt = service.ingest(tx, null);

            assertThat(receipt).isEqualTo(new IngestionReceipt(tx.transactionId(), EVENT_ID, NOW, false));
            verifyNoInteractions(idempotency);
        }

        @Test
        @DisplayName("AC-001-02: accepted transaction is handed to the publisher port")
        void publishesReceivedTransaction() {
            Transaction tx = aTransactionOccurredAt(NOW.minusSeconds(2));

            service.ingest(tx, null);

            ArgumentCaptor<ReceivedTransaction> captor = ArgumentCaptor.forClass(ReceivedTransaction.class);
            verify(publisher).publish(captor.capture());
            assertThat(captor.getValue()).isEqualTo(new ReceivedTransaction(EVENT_ID, NOW, tx));
        }

        @Test
        @DisplayName("AC-001-05: occurredAt more than 5 min in the future is rejected and not published")
        void rejectsTransactionsTooFarInTheFuture() {
            Transaction tx = aTransactionOccurredAt(NOW.plus(MAX_SKEW).plusSeconds(1));

            assertThatThrownBy(() -> service.ingest(tx, null))
                    .isInstanceOf(TransactionRejectedException.class)
                    .hasMessageContaining("occurredAt");
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("AC-001-05: occurredAt within clock-skew tolerance is accepted")
        void acceptsTransactionsWithinSkewTolerance() {
            assertThat(service.ingest(aTransactionOccurredAt(NOW.plus(MAX_SKEW)), null).eventId()).isEqualTo(EVENT_ID);
        }

        @Test
        @DisplayName("AC-001-06: publishing failure propagates so the caller never sees success")
        void propagatesPublishingFailure() {
            doThrow(new EventPublishingException("broker unavailable", null)).when(publisher).publish(any());

            assertThatThrownBy(() -> service.ingest(aTransactionOccurredAt(NOW), null))
                    .isInstanceOf(EventPublishingException.class);
        }
    }

    @Nested
    class WithIdempotencyKey {

        private final Transaction tx = aTransactionOccurredAt(NOW);

        @Test
        @DisplayName("AC-002-05: first request claims the key, publishes, and stores the receipt")
        void firstRequestPublishesAndCompletes() {
            given(idempotency.claim(KEY, tx.fingerprint())).willReturn(Optional.empty());

            IngestionReceipt receipt = service.ingest(tx, KEY);

            verify(publisher).publish(any());
            verify(idempotency).complete(KEY, IdempotencyRecord.completed(tx.fingerprint(), receipt));
            assertThat(receipt.replayed()).isFalse();
        }

        @Test
        @DisplayName("AC-002-05: repeated key with same body replays the original receipt without publishing")
        void replaysCompletedRequest() {
            IngestionReceipt original = new IngestionReceipt(tx.transactionId(), UUID.randomUUID(), NOW.minusSeconds(60), false);
            given(idempotency.claim(KEY, tx.fingerprint()))
                    .willReturn(Optional.of(IdempotencyRecord.completed(tx.fingerprint(), original)));

            IngestionReceipt receipt = service.ingest(tx, KEY);

            assertThat(receipt.eventId()).isEqualTo(original.eventId());
            assertThat(receipt.receivedAt()).isEqualTo(original.receivedAt());
            assertThat(receipt.replayed()).isTrue();
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("AC-002-06: repeated key with a different body → IdempotencyKeyReusedException")
        void rejectsKeyReuseWithDifferentBody() {
            given(idempotency.claim(KEY, tx.fingerprint()))
                    .willReturn(Optional.of(IdempotencyRecord.inProgress("some-other-fingerprint")));

            assertThatThrownBy(() -> service.ingest(tx, KEY)).isInstanceOf(IdempotencyKeyReusedException.class);
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("AC-002-07: key still in flight → IdempotentRequestInProgressException")
        void rejectsConcurrentDuplicate() {
            given(idempotency.claim(KEY, tx.fingerprint()))
                    .willReturn(Optional.of(IdempotencyRecord.inProgress(tx.fingerprint())));

            assertThatThrownBy(() -> service.ingest(tx, KEY))
                    .isInstanceOf(IdempotentRequestInProgressException.class);
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("AC-002-05: publish failure releases the key so the client can retry")
        void releasesKeyOnFailure() {
            given(idempotency.claim(KEY, tx.fingerprint())).willReturn(Optional.empty());
            doThrow(new EventPublishingException("down", null)).when(publisher).publish(any());

            assertThatThrownBy(() -> service.ingest(tx, KEY)).isInstanceOf(EventPublishingException.class);

            verify(idempotency).release(KEY);
            verify(idempotency, never()).complete(any(), any());
        }
    }
}
