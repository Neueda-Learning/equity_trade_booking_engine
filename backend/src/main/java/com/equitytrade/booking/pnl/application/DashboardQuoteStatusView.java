package com.equitytrade.booking.pnl.application;

import java.util.List;

public record DashboardQuoteStatusView(
        int available,
        int unavailable,
        int cached,
        int stale,
        int mock) {

    static DashboardQuoteStatusView from(
            List<PositionPnlView> items) {
        return new DashboardQuoteStatusView(
                count(items, PositionPnlView::available),
                count(items, item -> !item.available()),
                count(items, item -> item.available() && item.cached()),
                count(items, item -> item.available() && item.stale()),
                count(items, item -> item.available() && item.mock()));
    }

    private static int count(
            List<PositionPnlView> items,
            java.util.function.Predicate<PositionPnlView> predicate) {
        return Math.toIntExact(items.stream().filter(predicate).count());
    }
}
