package com.fraudplatform.alerts.application;

import com.fraudplatform.alerts.domain.AlertStatus;
import java.time.Instant;
import java.util.UUID;

public record AlertResolution(UUID alertId, String transactionId, String accountId, AlertStatus resolution,
        String resolvedBy, Instant resolvedAt) {}
