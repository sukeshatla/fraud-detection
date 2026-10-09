package com.fraudplatform.ingestion.application;

/** The event could not be made durable. The caller should retry later. */
public class EventPublishingException extends RuntimeException {

    public EventPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
