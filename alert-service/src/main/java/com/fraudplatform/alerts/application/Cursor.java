package com.fraudplatform.alerts.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Position in the queue ordered by {@code (created_at DESC, id DESC)}. The id breaks ties between
 * alerts created in the same microsecond. Opaque to clients (base64url), so the format can change.
 */
public record Cursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    public static Cursor of(AlertView alert) {
        return new Cursor(alert.createdAt(), alert.id());
    }
}
