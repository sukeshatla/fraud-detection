package com.fraudplatform.alerts.application;

import java.util.List;

public record AlertDetails(AlertView alert, List<AuditEntry> history) {}
