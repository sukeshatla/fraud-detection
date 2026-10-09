package com.fraudplatform.scoring.domain.rules;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.util.Optional;

/** The account was declined for fraud recently: its next transactions start from a high baseline. */
public class KnownHighRiskAccountRule implements FraudRule {

    static final int WEIGHT = 50;

    @Override
    public String code() {
        return "KNOWN_HIGH_RISK";
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
        return activity.knownHighRisk()
                ? Optional.of(new RuleHit(code(), WEIGHT, "account was recently declined for fraud"))
                : Optional.empty();
    }
}
