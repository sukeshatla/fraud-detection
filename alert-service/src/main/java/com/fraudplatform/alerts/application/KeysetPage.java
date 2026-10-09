package com.fraudplatform.alerts.application;

import java.util.List;
import java.util.Optional;

public record KeysetPage(List<AlertView> items, Optional<Cursor> next) {}
