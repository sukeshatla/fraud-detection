package com.fraudplatform.contracts;

/** Kafka record header names shared by producers and consumers. */
public final class EventHeaders {

    public static final String EVENT_TYPE = "event-type";
    public static final String SCHEMA_VERSION = "schema-version";
    /** Correlation id from the original HTTP request (X-Request-Id), carried hop to hop. */
    public static final String REQUEST_ID = "x-request-id";
    /** W3C trace context, so one distributed trace spans HTTP → Kafka → … */
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";

    private EventHeaders() {}
}
