package com.fraudplatform.scoring.application;

/**
 * The incoming event can never be processed (malformed or incomplete). Retrying is pointless,
 * so the error handler routes it straight to the dead-letter topic.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
