package com.fraudplatform.ingestion.application;

import com.fraudplatform.ingestion.domain.ReceivedTransaction;

/**
 * Outbound port: makes an accepted transaction durable for downstream consumers.
 *
 * <p>Implementations must return only once the transaction is durably stored, and throw
 * {@link EventPublishingException} otherwise. The API returns {@code 202} on the strength of it.
 */
public interface TransactionPublisher {

    void publish(ReceivedTransaction transaction);
}
