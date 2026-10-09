package com.fraudplatform.ingestion.application;

import java.util.UUID;

/** Generates event identifiers. A port so that tests can be deterministic. */
@FunctionalInterface
public interface EventIdGenerator {

    UUID next();
}
