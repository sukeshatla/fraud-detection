package com.fraudplatform.scoring.infrastructure.redis;

import com.fraudplatform.scoring.application.AccountActivityStore;
import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Sliding-window account activity in Redis sorted sets: one atomic Lua call per transaction.
 * See {@code scripts/account_activity.lua} for the data layout.
 */
public class RedisAccountActivityStore implements AccountActivityStore {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/account_activity.lua"), List.class);

    private final StringRedisTemplate redis;
    private final BigDecimal smallAmountThreshold;
    private final Duration retention;

    public RedisAccountActivityStore(StringRedisTemplate redis, BigDecimal smallAmountThreshold, Duration retention) {
        this.redis = redis;
        this.smallAmountThreshold = smallAmountThreshold;
        this.retention = retention;
    }

    @Override
    public AccountActivity recordAndGet(Transaction tx) {
        String tag = "{" + tx.accountId() + "}"; // hash tag → same cluster slot
        List<?> r = redis.execute(SCRIPT,
                List.of("velocity:" + tag, "small:" + tag, "last:" + tag),
                tx.eventId().toString(),
                String.valueOf(tx.occurredAt().toEpochMilli()),
                tx.amount().compareTo(smallAmountThreshold) < 0 ? "1" : "0",
                tx.country(),
                String.valueOf(retention.toMillis()));

        String previousCountry = (String) r.get(4);
        String previousTs = (String) r.get(5);
        return new AccountActivity(
                toInt(r.get(0)), toInt(r.get(1)), toInt(r.get(2)), toInt(r.get(3)),
                previousCountry.isEmpty() ? null : previousCountry,
                previousTs.isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(previousTs)));
    }

    private static int toInt(Object value) {
        return ((Number) value).intValue();
    }
}
