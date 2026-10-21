package com.nexus.core.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The time for dated keys ({@code fromDate}, {@code statusDate}). Columns keep milliseconds, so two changes in the
 * same millisecond would collide on the primary key; this clock never returns the same millisecond twice in one
 * server. UTC, like every LocalDateTime in Nexus.
 */
public final class CoreClock {

    private static final AtomicLong LAST = new AtomicLong();

    private CoreClock() {
    }

    public static LocalDateTime now() {
        long ms = LAST.updateAndGet(prev -> Math.max(System.currentTimeMillis(), prev + 1));
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
    }
}
