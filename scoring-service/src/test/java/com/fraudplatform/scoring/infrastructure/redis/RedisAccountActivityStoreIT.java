package com.fraudplatform.scoring.infrastructure.redis;

import static com.fraudplatform.scoring.domain.TransactionBuilder.NOW;
import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.RedisTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisAccountActivityStoreIT {

    @Container
    static final GenericContainer<?> REDIS = Containers.redis();

    private static RedisAccountActivityStore store;

    @BeforeAll
    static void connect() {
        StringRedisTemplate redis = RedisTestSupport.template(REDIS);
        store = new RedisAccountActivityStore(redis, new BigDecimal("2.00"), Duration.ofHours(24));
    }

    @Test
    @DisplayName("First transaction: counts of 1, no previous country")
    void firstTransaction() {
        AccountActivity activity = store.recordAndGet(tx(uniqueAccount(), "US", "50.00", 0));

        assertThat(activity).isEqualTo(new AccountActivity(1, 1, 1, 0, null, null));
    }

    @Test
    @DisplayName("AC-003-03: 60 s window counts only recent transactions (event time)")
    void sixtySecondWindow() {
        String acc = uniqueAccount();
        store.recordAndGet(tx(acc, "US", "10", -3600)); // 1h ago: in 1h/24h windows only
        store.recordAndGet(tx(acc, "US", "10", -120));  // 2 min ago
        store.recordAndGet(tx(acc, "US", "10", -30));
        AccountActivity activity = store.recordAndGet(tx(acc, "US", "10", 0));

        assertThat(activity.txCountLast60s()).isEqualTo(2);
        assertThat(activity.txCountLast1h()).isEqualTo(4);
        assertThat(activity.txCountLast24h()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-003-06: small-amount window counts only probes under the threshold")
    void smallAmountWindow() {
        String acc = uniqueAccount();
        store.recordAndGet(tx(acc, "US", "1.00", -100));
        store.recordAndGet(tx(acc, "US", "500.00", -50));
        AccountActivity activity = store.recordAndGet(tx(acc, "US", "0.50", 0));

        assertThat(activity.smallTxCountLast5m()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-003-04: previous country and time come from the prior transaction")
    void previousTransaction() {
        String acc = uniqueAccount();
        store.recordAndGet(tx(acc, "US", "10", -600));
        AccountActivity activity = store.recordAndGet(tx(acc, "MT", "10", 0));

        assertThat(activity.previousCountry()).isEqualTo("US");
        assertThat(activity.previousOccurredAt()).isEqualTo(NOW.minusSeconds(600));
    }

    @Test
    @DisplayName("AC-003-08: redelivering the same eventId does not inflate counts or lose the previous txn")
    void idempotentPerEventId() {
        String acc = uniqueAccount();
        store.recordAndGet(tx(acc, "US", "10", -600));
        Transaction current = tx(acc, "MT", "10", 0);

        AccountActivity first = store.recordAndGet(current);
        AccountActivity redelivered = store.recordAndGet(current);

        assertThat(redelivered).isEqualTo(first);
        assertThat(redelivered.txCountLast24h()).isEqualTo(2);
        assertThat(redelivered.previousCountry()).isEqualTo("US");
    }

    @Test
    @DisplayName("An out-of-order (older) event does not replace the latest transaction")
    void outOfOrderEventDoesNotRewindLast() {
        String acc = uniqueAccount();
        store.recordAndGet(tx(acc, "US", "10", 0));
        store.recordAndGet(tx(acc, "GB", "10", -300)); // arrives late
        AccountActivity next = store.recordAndGet(tx(acc, "US", "10", 10));

        assertThat(next.previousCountry()).isEqualTo("US");
    }

    private static Transaction tx(String account, String country, String amount, long offsetSeconds) {
        return aTransaction().eventId(UUID.randomUUID()).accountId(account).country(country)
                .amount(amount).occurredAt(NOW.plusSeconds(offsetSeconds)).build();
    }

    private static String uniqueAccount() {
        return "acc-" + UUID.randomUUID();
    }
}
