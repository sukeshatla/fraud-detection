package com.fraudplatform.ingestion.application;

import com.fraudplatform.ingestion.domain.ReceivedTransaction;
import com.fraudplatform.ingestion.domain.Transaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Use case: accept a transaction for fraud scoring.
 *
 * <p>Framework-free on purpose. It is wired as a bean in the infrastructure layer, so it can be
 * unit-tested with plain constructors and a fixed {@link Clock}.
 */
public class IngestTransactionService {

    private final TransactionPublisher publisher;
    private final IdempotencyStore idempotency;
    private final Clock clock;
    private final EventIdGenerator eventIds;
    private final Duration maxClockSkew;

    public IngestTransactionService(
            TransactionPublisher publisher,
            IdempotencyStore idempotency,
            Clock clock,
            EventIdGenerator eventIds,
            Duration maxClockSkew) {
        this.publisher = publisher;
        this.idempotency = idempotency;
        this.clock = clock;
        this.eventIds = eventIds;
        this.maxClockSkew = maxClockSkew;
    }

    /**
     * @param idempotencyKey optional client-supplied key; when present, retries return the original
     *                       receipt instead of publishing again
     */
    public IngestionReceipt ingest(Transaction transaction, String idempotencyKey) {
        if (idempotencyKey == null) {
            return publish(transaction);
        }

        String fingerprint = transaction.fingerprint();
        Optional<IdempotencyRecord> existing = idempotency.claim(idempotencyKey, fingerprint);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, fingerprint);
        }

        IngestionReceipt receipt;
        try {
            receipt = publish(transaction);
        } catch (RuntimeException e) {
            idempotency.release(idempotencyKey);
            throw e;
        }
        idempotency.complete(idempotencyKey, IdempotencyRecord.completed(fingerprint, receipt));
        return receipt;
    }

    private IngestionReceipt publish(Transaction transaction) {
        Instant receivedAt = clock.instant();
        rejectIfFromTheFuture(transaction, receivedAt);

        ReceivedTransaction received = new ReceivedTransaction(eventIds.next(), receivedAt, transaction);
        publisher.publish(received);

        return new IngestionReceipt(transaction.transactionId(), received.eventId(), receivedAt, false);
    }

    private static IngestionReceipt replay(IdempotencyRecord record, String key, String fingerprint) {
        if (!record.fingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(key);
        }
        return switch (record.status()) {
            case IN_PROGRESS -> throw new IdempotentRequestInProgressException(key);
            case COMPLETED -> record.receipt().asReplay();
        };
    }

    private void rejectIfFromTheFuture(Transaction transaction, Instant now) {
        Instant latestAllowed = now.plus(maxClockSkew);
        if (transaction.occurredAt().isAfter(latestAllowed)) {
            throw new TransactionRejectedException(
                    "occurredAt %s is more than %s in the future".formatted(transaction.occurredAt(), maxClockSkew));
        }
    }
}
