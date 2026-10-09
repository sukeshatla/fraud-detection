package com.fraudplatform.contracts;

/** Kafka record header names shared by producers and consumers. */
public final class EventHeaders {

    public static final String EVENT_TYPE = "event-type";
    public static final String SCHEMA_VERSION = "schema-version";

    private EventHeaders() {}
}
