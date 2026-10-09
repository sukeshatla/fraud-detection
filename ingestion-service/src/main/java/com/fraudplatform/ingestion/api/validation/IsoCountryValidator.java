package com.fraudplatform.ingestion.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class IsoCountryValidator implements ConstraintValidator<IsoCountry, String> {

    private static final Set<String> CODES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || CODES.contains(value);
    }
}
