package com.fraudplatform.scoring.domain.rules;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Duration;
import java.util.Optional;

/** "Impossible travel": the same card used in two countries closer together in time than a flight allows. */
public class GeoVelocityRule implements FraudRule {

    static final int WEIGHT = 35;

    private final Duration window;

    public GeoVelocityRule(Duration window) {
        this.window = window;
    }

    @Override
    public String code() {
        return "GEO_VELOCITY";
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
        if (activity.previousCountry() == null || activity.previousOccurredAt() == null
                || activity.previousCountry().equals(tx.country())) {
            return Optional.empty();
        }
        Duration gap = Duration.between(activity.previousOccurredAt(), tx.occurredAt()).abs();
        if (gap.compareTo(window) > 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(code(), WEIGHT,
                "country changed %s -> %s within %d min".formatted(activity.previousCountry(), tx.country(), gap.toMinutes())));
    }
}
