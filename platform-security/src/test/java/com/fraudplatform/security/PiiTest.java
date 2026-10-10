package com.fraudplatform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PiiTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(nullValues = "NULL", value = {
        "acc-1001,acc-****1001", "acc-fraud-1a2b3c4d,acc-****3c4d", "x1234567,****4567", "abc,****", "NULL,****"})
    @DisplayName("AC-015-04: account ids are masked to the last 4 characters")
    void masks(String in, String out) {
        assertThat(Pii.maskAccount(in)).isEqualTo(out);
    }
}
