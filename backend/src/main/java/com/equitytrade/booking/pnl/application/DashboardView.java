package com.equitytrade.booking.pnl.application;

import java.time.Instant;
import java.util.List;

public record DashboardView(
        PnlTotalsView totals,
        List<PositionPnlView> positions,
        int accountCount,
        int activeAccountCount,
        List<RecentActivityView> recentActivity,
        DashboardQuoteStatusView quoteStatus,
        Instant capturedAt) {
}
