package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.DashboardActivity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecentActivityView(
        UUID id,
        UUID accountId,
        String accountName,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        String status,
        Instant executedAt,
        Instant cancelledAt,
        String cancellationReason) {

    static RecentActivityView from(DashboardActivity activity) {
        return new RecentActivityView(
                activity.id(),
                activity.accountId(),
                activity.accountName(),
                activity.ticker(),
                activity.side(),
                PnlDecimal.api(activity.quantity()),
                PnlDecimal.api(activity.tradePrice()),
                activity.status(),
                activity.executedAt(),
                activity.cancelledAt(),
                activity.cancellationReason());
    }
}
