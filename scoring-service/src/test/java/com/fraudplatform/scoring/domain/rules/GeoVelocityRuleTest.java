package com.fraudplatform.scoring.domain.rules;

import static com.fraudplatform.scoring.domain.TransactionBuilder.NOW;
import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.RuleHit;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeoVelocityRuleTest {

    private final GeoVelocityRule rule = new GeoVelocityRule(Duration.ofHours(1));

    @Test
    @DisplayName("AC-003-04: different country within 1 h of previous txn fires with weight 35")
    void firesOnImpossibleTravel() {
        assertThat(rule.evaluate(aTransaction().country("MT").build(), previous("US", NOW.minusSeconds(1800))))
                .get().extracting(RuleHit::code, RuleHit::weight).containsExactly("GEO_VELOCITY", 35);
    }

    @Test
    void sameCountryDoesNotFire() {
        assertThat(rule.evaluate(aTransaction().country("US").build(), previous("US", NOW.minusSeconds(60)))).isEmpty();
    }

    @Test
    @DisplayName("AC-003-04: country change after more than 1 h is plausible travel")
    void outsideWindowDoesNotFire() {
        assertThat(rule.evaluate(aTransaction().country("MT").build(), previous("US", NOW.minus(Duration.ofHours(2))))).isEmpty();
    }

    @Test
    void firstTransactionDoesNotFire() {
        assertThat(rule.evaluate(aTransaction().build(), AccountActivity.none())).isEmpty();
    }

    private static AccountActivity previous(String country, Instant at) {
        return new AccountActivity(1, 1, 1, 0, country, at);
    }
}
