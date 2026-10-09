package com.fraudplatform.scoring.domain.ml;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Transaction;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a transaction + account activity into the model's input vector.
 *
 * <p><b>Mirror of {@code extract()} in ml/train.py.</b> Any change here must be made there too
 * (and vice versa); {@code ModelParityTest} fails otherwise. That's training/serving skew, the
 * most common way ML systems silently break in production.
 */
public class FeatureExtractor {

    public static final List<String> FEATURE_NAMES = List.of(
            "log_amount_usd", "hour_sin", "hour_cos", "high_risk_mcc", "card_not_present",
            "log_tx_count_1h", "log_tx_count_24h", "country_changed", "log_small_tx_5m");

    private final Map<String, BigDecimal> usdRates;
    private final Set<String> highRiskMccs;

    public FeatureExtractor(Map<String, BigDecimal> usdRates, Set<String> highRiskMccs) {
        this.usdRates = Map.copyOf(usdRates);
        this.highRiskMccs = Set.copyOf(highRiskMccs);
    }

    public double[] extract(Transaction tx, AccountActivity activity) {
        double amountUsd = tx.amount().multiply(usdRates.getOrDefault(tx.currency(), BigDecimal.ONE)).doubleValue();
        ZonedDateTime utc = tx.occurredAt().atZone(ZoneOffset.UTC);
        double hour = utc.getHour() + utc.getMinute() / 60.0 + utc.getSecond() / 3600.0;
        double angle = 2 * Math.PI * hour / 24;
        boolean countryChanged = activity.previousCountry() != null && !activity.previousCountry().equals(tx.country());

        return new double[] {
            Math.log1p(amountUsd),
            Math.sin(angle),
            Math.cos(angle),
            flag(highRiskMccs.contains(tx.merchantCategoryCode())),
            flag("CARD_NOT_PRESENT".equals(tx.channel())),
            Math.log1p(activity.txCountLast1h()),
            Math.log1p(activity.txCountLast24h()),
            flag(countryChanged),
            Math.log1p(activity.smallTxCountLast5m()),
        };
    }

    private static double flag(boolean value) {
        return value ? 1.0 : 0.0;
    }
}
