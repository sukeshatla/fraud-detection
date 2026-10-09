package com.fraudplatform.scoring.infrastructure.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: 48-bit Unix-millisecond timestamp, then version/variant bits, then 74
 * "random" bits. Here the random bits are derived from a SHA-256 of {@code name}, so the id is both
 * <b>time-ordered</b> (index-friendly) and <b>deterministic</b> (replays map to the same row).
 *
 * <pre>
 *  0                   1                   2                   3
 *  |           unix_ts_ms (48)            |ver|  rand_a (12) |var|        rand_b (62)        |
 * </pre>
 */
public final class UuidV7 {

    private UuidV7() {}

    public static UUID fromTimeAndName(Instant time, String name) {
        ByteBuffer hash = ByteBuffer.wrap(sha256(name));
        long randA = hash.getShort() & 0x0FFFL;
        long randB = hash.getLong() & 0x3FFF_FFFF_FFFF_FFFFL;

        long msb = (time.toEpochMilli() << 16) | (0x7L << 12) | randA;
        long lsb = (0x2L << 62) | randB; // variant 10xx
        return new UUID(msb, lsb);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", e);
        }
    }
}
