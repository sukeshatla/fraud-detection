package com.fraudplatform.ingestion.domain;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * A positive monetary amount. {@link BigDecimal}, never {@code double}: binary floating point
 * cannot represent 0.10 exactly, and rounding errors compound across millions of transactions.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Guard.required(amount, "amount");
        Guard.required(currency, "currency");
        if (amount.signum() <= 0) {
            throw new DomainValidationException("amount must be positive");
        }
        int minorUnits = currency.getDefaultFractionDigits();
        if (minorUnits >= 0 && amount.stripTrailingZeros().scale() > minorUnits) {
            throw new DomainValidationException(
                    "amount has more than %d fraction digits for %s".formatted(minorUnits, currency));
        }
    }
}
