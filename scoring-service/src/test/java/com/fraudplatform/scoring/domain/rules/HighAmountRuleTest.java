package com.fraudplatform.scoring.domain.rules;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RuleHit;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule(new BigDecimal("5000"), Map.of("EUR", new BigDecimal("1.10")));

    @Test
    @DisplayName("AC-003-02: amount at the threshold fires with weight 30")
    void firesAtThreshold() {
        assertThat(rule.evaluate(aTransaction().amount("5000.00").build(), AccountActivity.none()))
                .get().extracting(RuleHit::code, RuleHit::weight).containsExactly("HIGH_AMOUNT", 30);
    }

    @Test
    void doesNotFireBelowThreshold() {
        assertThat(rule.evaluate(aTransaction().amount("4999.99").build(), AccountActivity.none())).isEmpty();
    }

    @Test
    @DisplayName("AC-003-02: threshold is USD-equivalent (4,600 EUR × 1.10 = 5,060 USD fires)")
    void convertsToUsd() {
        assertThat(rule.evaluate(aTransaction().amount("4600").currency("EUR").build(), AccountActivity.none())).isPresent();
        assertThat(rule.evaluate(aTransaction().amount("4500").currency("EUR").build(), AccountActivity.none())).isEmpty();
    }
}
