package com.fraudplatform.scoring.domain.rules;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RuleHit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardTestingRuleTest {

    private final CardTestingRule rule = new CardTestingRule(new BigDecimal("2.00"), 3);

    @Test
    @DisplayName("AC-003-06: 3rd transaction under 2.00 within 5 min fires with weight 45")
    void firesOnThirdSmallTransaction() {
        assertThat(rule.evaluate(aTransaction().amount("1.00").build(), smallCount(3)))
                .get().extracting(RuleHit::code, RuleHit::weight).containsExactly("CARD_TESTING", 45);
    }

    @Test
    void twoSmallTransactionsDoNotFire() {
        assertThat(rule.evaluate(aTransaction().amount("1.00").build(), smallCount(2))).isEmpty();
    }

    @Test
    @DisplayName("A large purchase after small probes is not itself a probe")
    void largeTransactionDoesNotFire() {
        assertThat(rule.evaluate(aTransaction().amount("250.00").build(), smallCount(5))).isEmpty();
    }

    private static AccountActivity smallCount(int n) {
        return new AccountActivity(n, n, n, n, null, null);
    }
}
