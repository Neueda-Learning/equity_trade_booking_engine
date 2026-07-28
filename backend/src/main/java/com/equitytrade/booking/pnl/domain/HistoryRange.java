package com.equitytrade.booking.pnl.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public enum HistoryRange {
    ONE_DAY("1D", Duration.ofDays(1)),
    SEVEN_DAYS("7D", Duration.ofDays(7)),
    THIRTY_DAYS("30D", Duration.ofDays(30)),
    ALL("ALL", null);

    private final String apiValue;
    private final Duration duration;

    HistoryRange(String apiValue, Duration duration) {
        this.apiValue = apiValue;
        this.duration = duration;
    }

    public String apiValue() {
        return apiValue;
    }

    public Optional<Instant> capturedFrom(Instant now) {
        return duration == null
                ? Optional.empty()
                : Optional.of(now.minus(duration));
    }

    public static HistoryRange parse(String value) {
        for (HistoryRange range : values()) {
            if (range.apiValue.equals(value)) {
                return range;
            }
        }
        throw new IllegalArgumentException(
                "must be one of 1D, 7D, 30D or ALL");
    }
}
