package com.equitytrade.booking.pnl.application;

import java.util.Locale;

public enum DashboardHistorySource {
    LOCAL,
    PROVIDER,
    HYBRID;

    static DashboardHistorySource parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException(
                    "DASHBOARD_HISTORY_SOURCE must be local, provider or hybrid",
                    exception);
        }
    }
}
