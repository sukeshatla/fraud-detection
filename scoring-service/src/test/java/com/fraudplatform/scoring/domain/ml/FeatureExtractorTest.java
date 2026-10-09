package com.fraudplatform.scoring.domain.ml;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fraudplatform.scoring.domain.AccountActivity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureExtractorTest {

    private final FeatureExtractor extractor = new FeatureExtractor(Map.of("EUR", new BigDecimal("1.10")), Set.of("7995"));

    @Test
    @DisplayName("AC-006-01: nine features in the documented order")
    void extractsAllFeatures() {
        var tx = aTransaction().amount("100.00").currency("EUR").mcc("7995").channel("CARD_NOT_PRESENT").country("MT")
                .occurredAt(Instant.parse("2026-10-09T06:00:00Z")).build();
        var activity = new AccountActivity(2, 4, 9, 1, "US", Instant.parse("2026-10-09T05:30:00Z"));

        double[] x = extractor.extract(tx, activity);

        assertThat(x).hasSize(FeatureExtractor.FEATURE_NAMES.size());
        assertThat(x[0]).isCloseTo(Math.log1p(110.0), within(1e-12)); // EUR → USD
        assertThat(x[1]).isCloseTo(1.0, within(1e-12));               // sin(2π·6/24)
        assertThat(x[2]).isCloseTo(0.0, within(1e-12));               // cos(2π·6/24)
        assertThat(x[3]).isEqualTo(1.0);                              // high-risk MCC
        assertThat(x[4]).isEqualTo(1.0);                              // card not present
        assertThat(x[5]).isCloseTo(Math.log1p(4), within(1e-12));
        assertThat(x[6]).isCloseTo(Math.log1p(9), within(1e-12));
        assertThat(x[7]).isEqualTo(1.0);                              // US → MT
        assertThat(x[8]).isCloseTo(Math.log1p(1), within(1e-12));
    }

    @Test
    @DisplayName("Hour is cyclical: 23:59 and 00:01 are neighbours, not opposite ends")
    void hourIsCyclical() {
        double[] lateNight = extractor.extract(aTransaction().occurredAt(Instant.parse("2026-10-09T23:59:00Z")).build(), AccountActivity.none());
        double[] earlyMorning = extractor.extract(aTransaction().occurredAt(Instant.parse("2026-10-10T00:01:00Z")).build(), AccountActivity.none());

        assertThat(Math.hypot(lateNight[1] - earlyMorning[1], lateNight[2] - earlyMorning[2])).isLessThan(0.01);
    }

    @Test
    void noPreviousCountryMeansNoChange() {
        assertThat(extractor.extract(aTransaction().build(), AccountActivity.none())[7]).isZero();
    }
}
