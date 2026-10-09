package com.fraudplatform.scoring.application;

import java.util.UUID;

/** Outbound port: remembers which events were fully processed (consumer-side dedupe). */
public interface ProcessedEventStore {

    boolean isProcessed(UUID eventId);

    void markProcessed(UUID eventId);
}
