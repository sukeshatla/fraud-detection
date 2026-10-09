package com.fraudplatform.scoring.domain.rules;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RuleHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnownHighRiskAccountRuleTest {

    private final KnownHighRiskAccountRule rule = new KnownHighRiskAccountRule();

    @Test
    @DisplayName("AC-005-02: account flagged in the risk cache adds weight 50")
    void firesForFlaggedAccount() {
        assertThat(rule.evaluate(aTransaction().build(), AccountActivity.none().withKnownHighRisk(true)))
                .get().extracting(RuleHit::code, RuleHit::weight).containsExactly("KNOWN_HIGH_RISK", 50);
    }

    @Test
    void silentForUnflaggedAccount() {
        assertThat(rule.evaluate(aTransaction().build(), AccountActivity.none())).isEmpty();
    }
}
