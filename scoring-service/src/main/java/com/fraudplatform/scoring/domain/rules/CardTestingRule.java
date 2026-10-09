package com.fraudplatform.scoring.domain.rules;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.math.BigDecimal;
import java.util.Optional;

/** Fraudsters "test" stolen cards with tiny purchases before a big one. */
public class CardTestingRule implements FraudRule {

    static final int WEIGHT = 45;

    private final BigDecimal smallAmount;
    private final int minSmallTransactions;

    public CardTestingRule(BigDecimal smallAmount, int minSmallTransactions) {
        this.smallAmount = smallAmount;
        this.minSmallTransactions = minSmallTransactions;
    }

    public BigDecimal smallAmount() {
        return smallAmount;
    }

    @Override
    public String code() {
        return "CARD_TESTING";
    }

    @Override
    public Optional<RuleHit> evaluate(Transaction tx, AccountActivity activity) {
        boolean isProbe = tx.amount().compareTo(smallAmount) < 0;
        if (!isProbe || activity.smallTxCountLast5m() < minSmallTransactions) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(code(), WEIGHT,
                "%d transactions under %s in 5 min".formatted(activity.smallTxCountLast5m(), smallAmount.toPlainString())));
    }
}
