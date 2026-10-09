package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.Severity;
import java.time.Instant;
import java.util.Map;

/** Dashboard KPIs. */
public record AlertStats(
        Map<Severity, Long> openBySeverity,
        long underReview,
        long raised,
        long confirmedFraud,
        long falsePositives,
        Instant since) {}
