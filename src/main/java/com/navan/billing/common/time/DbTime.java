package com.navan.billing.common.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The one format for every TEXT timestamp column: fixed-width UTC with millis, e.g. {@code 2026-03-11T06:40:00.000Z}.
 *
 * <p>Fixed width matters: SQLite compares TEXT lexicographically, and {@link Instant#toString()} drops trailing
 * zero fractions ({@code …:00Z} vs {@code …:00.5Z}), which sorts wrong. Sub-millisecond precision is truncated.
 */
public final class DbTime {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private DbTime() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    public static Instant parse(String text) {
        return Instant.parse(text);
    }
}
