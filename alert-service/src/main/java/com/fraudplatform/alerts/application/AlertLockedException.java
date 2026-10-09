package com.fraudplatform.alerts.application;

import java.util.UUID;

/** Another request is updating this alert right now (layer 1 rejected us). */
public class AlertLockedException extends RuntimeException {

    public AlertLockedException(UUID id) {
        super("Alert " + id + " is being updated by another request");
    }
}
