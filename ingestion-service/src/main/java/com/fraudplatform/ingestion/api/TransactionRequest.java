package com.fraudplatform.ingestion.api;

import com.fraudplatform.ingestion.api.validation.IsoCountry;
import com.fraudplatform.ingestion.api.validation.IsoCurrency;
import com.fraudplatform.ingestion.domain.Channel;
import com.fraudplatform.ingestion.domain.Money;
import com.fraudplatform.ingestion.domain.Transaction;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/** Wire format for {@code POST /api/v1/transactions}. Syntactic validation lives here. */
public record TransactionRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String transactionId,
        @NotBlank @Size(max = 64) @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String accountId,
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("1000000")
        @Digits(integer = 7, fraction = 2)
        BigDecimal amount,
        @NotBlank @IsoCurrency String currency,
        @NotBlank @Size(max = 64) String merchantId,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits") String merchantCategoryCode,
        @NotBlank @IsoCountry String country,
        @NotNull Channel channel,
        @NotNull Instant occurredAt) {

    static final String ID_PATTERN = "[A-Za-z0-9_-]+";
    static final String ID_MESSAGE = "may contain only letters, digits, '-' and '_'";

    Transaction toDomain() {
        return new Transaction(
                transactionId,
                accountId,
                new Money(amount, Currency.getInstance(currency)),
                merchantId,
                merchantCategoryCode,
                country,
                channel,
                occurredAt);
    }
}
