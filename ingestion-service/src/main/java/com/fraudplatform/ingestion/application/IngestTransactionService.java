package com.fraudplatform.ingestion.application;

import com.fraudplatform.ingestion.domain.ReceivedTransaction;
import com.fraudplatform.ingestion.domain.Transaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Use case: accept a transaction for fraud scoring.
 *
 * <p>Framework-free on purpose. It is wired as a bean in the infrastructure layer, so it can be
 * unit-tested with plain constructors and a fixed {@link Clock}.
 */
public class IngestTransactionService {

    private final TransactionPublisher publisher;
    private final Clock clock;
    private final EventIdGenerator eventIds;
    private final Duration maxClockSkew;

    public IngestTransactionService(
            TransactionPublisher publisher, Clock clock, EventIdGenerator eventIds, Duration maxClockSkew) {
        this.publisher = publisher;
        this.clock = clock;
        this.eventIds = eventIds;
        this.maxClockSkew = maxClockSkew;
    }

    public IngestionReceipt ingest(Transaction transaction) {
        Instant receivedAt = clock.instant();
        rejectIfFromTheFuture(transaction, receivedAt);

        ReceivedTransaction received = new ReceivedTransaction(eventIds.next(), receivedAt, transaction);
        publisher.publish(received);

        return new IngestionReceipt(transaction.transactionId(), received.eventId(), receivedAt);
    }

    private void rejectIfFromTheFuture(Transaction transaction, Instant now) {
        Instant latestAllowed = now.plus(maxClockSkew);
        if (transaction.occurredAt().isAfter(latestAllowed)) {
            throw new TransactionRejectedException(
                    "occurredAt %s is more than %s in the future".formatted(transaction.occurredAt(), maxClockSkew));
        }
    }
}
