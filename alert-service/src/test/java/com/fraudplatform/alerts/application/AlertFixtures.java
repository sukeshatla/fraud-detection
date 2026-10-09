package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.alerts.domain.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AlertFixtures {

    public static final Instant NOW = Instant.parse("2026-10-09T19:00:00Z");

    private AlertFixtures() {}

    public static AlertView alert(UUID id, AlertStatus status, long version) {
        return new AlertView(id, "txn-" + id, "acc-1001", new BigDecimal("7500.00"), "USD", "m-1", "7995", "MT",
                "CARD_NOT_PRESENT", NOW.minusSeconds(5), 85, 85, null, null, "DECLINE", Severity.HIGH, status, version,
                NOW, NOW, List.of(new RuleHitView("HIGH_AMOUNT", 30, "big")));
    }

    public static NewAlert newAlert(UUID id, String transactionId, String accountId, Instant createdAt) {
        return new NewAlert(id, transactionId, accountId, new BigDecimal("7500.00"), "USD", "m-1", "7995", "MT",
                "CARD_NOT_PRESENT", createdAt.minusSeconds(1), 85, 85, null, null, "DECLINE", Severity.HIGH, createdAt,
                List.of(new RuleHitView("HIGH_AMOUNT", 30, "big"), new RuleHitView("HIGH_RISK_MCC", 20, "mcc")));
    }
}
