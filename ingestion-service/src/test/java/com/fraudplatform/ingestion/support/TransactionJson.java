package com.fraudplatform.ingestion.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Valid request bodies for HTTP-level tests. */
public final class TransactionJson {

    private TransactionJson() {}

    public static String valid(String transactionId, String accountId) {
        return """
                {
                  "transactionId": "%s",
                  "accountId": "%s",
                  "amount": 249.99,
                  "currency": "USD",
                  "merchantId": "m-5541",
                  "merchantCategoryCode": "5732",
                  "country": "US",
                  "channel": "CARD_NOT_PRESENT",
                  "occurredAt": "%s"
                }
                """.formatted(transactionId, accountId, Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
