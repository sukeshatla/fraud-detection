package com.fraudplatform.load;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Infinite feeder producing a realistic mix (AC-013-04): ~95% ordinary purchases across a large
 * account pool, ~5% known fraud patterns so the scoring, alerting and outbox paths do real work:
 *
 * <ul>
 *   <li><b>velocity burst</b>: 8 rapid transactions on one account
 *   <li><b>card testing</b>: 4 sub-$2 probes on one account
 *   <li><b>impossible travel</b>: a US then a MT transaction minutes apart, the second large and at a casino
 * </ul>
 * Patterns are emitted as consecutive records so they really hit one account in quick succession.
 * Thread-safe: Gatling may pull from several threads.
 */
public final class TransactionFeeder implements Iterator<Map<String, Object>> {

    static final int ACCOUNTS = 50_000;
    static final double FRAUD_SHARE = 0.05;
    private static final String[] NORMAL_MCCS = {"5411", "5812", "5732", "5999", "4111", "5541", "5912"};

    private final Deque<Map<String, Object>> pending = new ArrayDeque<>();

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public synchronized Map<String, Object> next() {
        if (pending.isEmpty()) {
            if (random().nextDouble() < FRAUD_SHARE) {
                enqueueFraudPattern();
            } else {
                pending.add(normal());
            }
        }
        return pending.poll();
    }

    private Map<String, Object> normal() {
        double amount = Math.exp(random().nextGaussian() * 0.9 + Math.log(45)); // lognormal around $45
        return tx("acc-" + random().nextInt(ACCOUNTS), amount, NORMAL_MCCS[random().nextInt(NORMAL_MCCS.length)], "US",
                random().nextDouble() < 0.4 ? "CARD_NOT_PRESENT" : "CARD_PRESENT", "normal");
    }

    private void enqueueFraudPattern() {
        String account = "acc-fraud-" + UUID.randomUUID().toString().substring(0, 8);
        switch (random().nextInt(3)) {
            case 0 -> {
                for (int i = 0; i < 8; i++) {
                    pending.add(tx(account, 20 + random().nextInt(80), "5732", "US", "CARD_NOT_PRESENT", "velocity"));
                }
            }
            case 1 -> {
                for (int i = 0; i < 4; i++) {
                    pending.add(tx(account, 0.5 + random().nextDouble(), "5999", "US", "CARD_NOT_PRESENT", "card-testing"));
                }
                pending.add(tx(account, 1200, "5732", "US", "CARD_NOT_PRESENT", "card-testing"));
            }
            default -> {
                pending.add(tx(account, 35, "5411", "US", "CARD_PRESENT", "impossible-travel"));
                pending.add(tx(account, 6500, "7995", "MT", "CARD_NOT_PRESENT", "impossible-travel"));
            }
        }
    }

    private static Map<String, Object> tx(String account, double amount, String mcc, String country, String channel, String kind) {
        Map<String, Object> row = new HashMap<>();
        row.put("transactionId", "lt-" + UUID.randomUUID());
        row.put("accountId", account);
        row.put("amount", String.format("%.2f", amount));
        row.put("mcc", mcc);
        row.put("country", country);
        row.put("channel", channel);
        row.put("occurredAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        row.put("kind", kind);
        return row;
    }

    private static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }
}
