package com.fraudplatform.scoring.domain.rules;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.util.Optional;
import java.util.Set;

/** Merchant categories with high fraud rates: gambling, quasi-cash, money transfer, crypto. */
public class HighRiskMccRule implements FraudRule {

    static final int WEIGHT = 20;

    private final Set<String> highRiskMccs;

    public HighRiskMccRule(Set<String> highRiskMccs) {
        this.highRiskMccs = Set.copyOf(highRiskMccs);
    }

    @Override
    public String code() {
        return "HIGH_RISK_MCC";
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
        if (!highRiskMccs.contains(tx.merchantCategoryCode())) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(code(), WEIGHT, "MCC %s is high risk".formatted(tx.merchantCategoryCode())));
    }
}
