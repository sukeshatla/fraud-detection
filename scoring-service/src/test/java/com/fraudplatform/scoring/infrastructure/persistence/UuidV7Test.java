package com.fraudplatform.scoring.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    private static final Instant T = Instant.parse("2026-10-09T18:15:30.123Z");

    @Test
    @DisplayName("AC-004-06: version 7, RFC 4122 variant, and the timestamp is recoverable from the high 48 bits")
    void layout() {
        UUID id = UuidV7.fromTimeAndName(T, "txn-1");

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(T.toEpochMilli());
    }

    @Test
    @DisplayName("AC-004-06: same time + name → same id (replays hit the same row)")
    void deterministic() {
        assertThat(UuidV7.fromTimeAndName(T, "txn-1")).isEqualTo(UuidV7.fromTimeAndName(T, "txn-1"));
        assertThat(UuidV7.fromTimeAndName(T, "txn-1")).isNotEqualTo(UuidV7.fromTimeAndName(T, "txn-2"));
    }

    @Test
    @DisplayName("AC-004-06: ids sort by time (B-tree inserts append on the right)")
    void timeOrdered() {
        UUID earlier = UuidV7.fromTimeAndName(T, "zzz");
        UUID later = UuidV7.fromTimeAndName(T.plusMillis(1), "aaa");

        // PostgreSQL compares uuid bytes as unsigned, big-endian; compare the same way
        assertThat(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits())).isNegative();
    }
}
