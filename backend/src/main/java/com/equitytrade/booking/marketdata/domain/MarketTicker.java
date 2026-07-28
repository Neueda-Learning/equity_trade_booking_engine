package com.equitytrade.booking.marketdata.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MarketTicker {

    private static final Pattern PATTERN =
            Pattern.compile("[A-Z][A-Z0-9.-]{0,9}");

    private MarketTicker() {
    }

    public static String normalize(String rawTicker) {
        if (rawTicker == null) {
            throw new IllegalArgumentException("is required");
        }
        String normalized = rawTicker.strip().toUpperCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "must match [A-Z][A-Z0-9.-]{0,9}");
        }
        return normalized;
    }
}
