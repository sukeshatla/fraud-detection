package com.fraudplatform.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    void acceptsPositiveAmountWithinCurrencyPrecision() {
        Money money = new Money(new BigDecimal("249.99"), USD);

        assertThat(money.amount()).isEqualByComparingTo("249.99");
        assertThat(money.currency()).isEqualTo(USD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    @DisplayName("Amount must be strictly positive")
    void rejectsNonPositiveAmounts(String amount) {
        assertThatThrownBy(() -> new Money(new BigDecimal(amount), USD))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Fraction digits may not exceed the currency's minor units (JPY has none)")
    void rejectsMorePrecisionThanCurrencyAllows() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100.5"), JPY))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("JPY");
    }

    @Test
    void trailingZerosBeyondPrecisionAreTolerated() {
        assertThat(new Money(new BigDecimal("100.00"), JPY).amount()).isEqualByComparingTo("100");
    }

    @Test
    void requiresAmountAndCurrency() {
        assertThatThrownBy(() -> new Money(null, USD)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(DomainValidationException.class);
    }
}
