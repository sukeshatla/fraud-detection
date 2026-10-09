package com.fraudplatform.alerts.application;

import java.util.List;

public record OffsetPage(List<AlertView> items, int page, int size, long totalElements) {}
