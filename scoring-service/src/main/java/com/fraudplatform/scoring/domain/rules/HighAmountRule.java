package com.fraudplatform.scoring.domain.rules;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Large single purchases, compared in USD-equivalent so the threshold means the same in every currency. */
public class HighAmountRule implements FraudRule {

    static final int WEIGHT = 30;

    private final BigDecimal thresholdUsd;
    private final Map<String, BigDecimal> usdRates;

    /** @param usdRates multiply an amount in that currency to get USD; missing currencies count 1:1 */
    public HighAmountRule(BigDecimal thresholdUsd, Map<String, BigDecimal> usdRates) {
        this.thresholdUsd = thresholdUsd;
        this.usdRates = Map.copyOf(usdRates);
    }

    @Override
    public String code() {
        return "HIGH_AMOUNT";
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
        BigDecimal usd = tx.amount().multiply(usdRates.getOrDefault(tx.currency(), BigDecimal.ONE));
        if (usd.compareTo(thresholdUsd) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(code(), WEIGHT,
                "amount %s %s >= %s USD".formatted(tx.amount().toPlainString(), tx.currency(), thresholdUsd.toPlainString())));
    }
}
