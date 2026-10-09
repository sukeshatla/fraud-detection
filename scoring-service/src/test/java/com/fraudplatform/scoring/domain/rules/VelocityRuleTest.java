package com.fraudplatform.scoring.domain.rules;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RuleHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule(5);

    @Test
    @DisplayName("AC-003-03: more than 5 transactions in 60 s fires with weight 40")
    void firesAboveLimit() {
        assertThat(rule.evaluate(aTransaction().build(), activityWithCount(6)))
                .get().extracting(RuleHit::code, RuleHit::weight).containsExactly("VELOCITY", 40);
    }

    @Test
    @DisplayName("AC-003-03: exactly 5 does not fire")
    void doesNotFireAtLimit() {
        assertThat(rule.evaluate(aTransaction().build(), activityWithCount(5))).isEmpty();
    }

    private static AccountActivity activityWithCount(int last60s) {
        return new AccountActivity(last60s, last60s, last60s, 0, null, null);
    }
}
