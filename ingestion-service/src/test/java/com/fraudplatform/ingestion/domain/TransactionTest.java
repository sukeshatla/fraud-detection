package com.fraudplatform.ingestion.domain;

import static com.fraudplatform.ingestion.domain.TransactionFixtures.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Money AMOUNT = new Money(BigDecimal.TEN, Currency.getInstance("USD"));

    @Test
    void createsValidTransaction() {
        Transaction tx = TransactionFixtures.aTransaction();

        assertThat(tx.transactionId()).isEqualTo("txn-7f3a9c");
        assertThat(tx.accountId()).isEqualTo("acc-1001");
        assertThat(tx.channel()).isEqualTo(Channel.CARD_NOT_PRESENT);
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThatThrownBy(() -> new Transaction(" ", "acc", AMOUNT, "m", "5411", "US", Channel.ATM, OCCURRED_AT))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("transactionId");
        assertThatThrownBy(() -> new Transaction("t", "", AMOUNT, "m", "5411", "US", Channel.ATM, OCCURRED_AT))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("accountId");
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatThrownBy(() -> new Transaction("t", "a", null, "m", "5411", "US", Channel.ATM, OCCURRED_AT))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("amount");
        assertThatThrownBy(() -> new Transaction("t", "a", AMOUNT, "m", "5411", "US", null, OCCURRED_AT))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("channel");
        assertThatThrownBy(() -> new Transaction("t", "a", AMOUNT, "m", "5411", "US", Channel.ATM, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("occurredAt");
    }

    @Test
    @DisplayName("AC-002-06: fingerprint is stable for equal transactions (amount scale ignored)")
    void fingerprintIsStable() {
        Transaction a = TransactionFixtures.aTransaction();
        Transaction b = new Transaction(a.transactionId(), a.accountId(),
                new Money(new BigDecimal("249.990"), a.amount().currency()), a.merchantId(),
                a.merchantCategoryCode(), a.country(), a.channel(), a.occurredAt());

        assertThat(a.fingerprint()).isEqualTo(b.fingerprint()).hasSize(64);
    }

    @Test
    @DisplayName("AC-002-06: fingerprint changes when any field changes")
    void fingerprintChangesWithContent() {
        Transaction a = TransactionFixtures.aTransaction();
        Transaction differentAmount = new Transaction(a.transactionId(), a.accountId(),
                new Money(new BigDecimal("1.00"), a.amount().currency()), a.merchantId(),
                a.merchantCategoryCode(), a.country(), a.channel(), a.occurredAt());
        Transaction differentCountry = new Transaction(a.transactionId(), a.accountId(), a.amount(),
                a.merchantId(), a.merchantCategoryCode(), "GB", a.channel(), a.occurredAt());

        assertThat(differentAmount.fingerprint()).isNotEqualTo(a.fingerprint());
        assertThat(differentCountry.fingerprint()).isNotEqualTo(a.fingerprint());
    }
}
