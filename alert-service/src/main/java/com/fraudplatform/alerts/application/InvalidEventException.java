package com.fraudplatform.alerts.application;

/** The incoming event can never be processed; routed straight to the dead-letter topic. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
