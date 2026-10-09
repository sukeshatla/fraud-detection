package com.fraudplatform.scoring.domain.rules;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.util.Optional;

/** Bursts of transactions on one account: typical of stolen-card cash-out. */
public class VelocityRule implements FraudRule {

    static final int WEIGHT = 40;

    private final int maxPerMinute;

    public VelocityRule(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    public String code() {
        return "VELOCITY";
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
        if (activity.txCountLast60s() <= maxPerMinute) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(code(), WEIGHT,
                "%d transactions in 60s (limit %d)".formatted(activity.txCountLast60s(), maxPerMinute)));
    }
}
