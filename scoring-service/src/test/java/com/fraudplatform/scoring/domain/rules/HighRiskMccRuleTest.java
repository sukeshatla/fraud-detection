package com.fraudplatform.scoring.domain.rules;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RuleHit;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HighRiskMccRuleTest {

    private final HighRiskMccRule rule = new HighRiskMccRule(Set.of("7995", "6051"));

    @Test
    @DisplayName("AC-003-05: gambling MCC 7995 fires with weight 20")
    void firesForHighRiskMcc() {
        assertThat(rule.evaluate(aTransaction().mcc("7995").build(), AccountActivity.none()))
                .get().extracting(RuleHit::code, RuleHit::weight).containsExactly("HIGH_RISK_MCC", 20);
    }

    @Test
    void groceryDoesNotFire() {
        assertThat(rule.evaluate(aTransaction().mcc("5411").build(), AccountActivity.none())).isEmpty();
    }
}
