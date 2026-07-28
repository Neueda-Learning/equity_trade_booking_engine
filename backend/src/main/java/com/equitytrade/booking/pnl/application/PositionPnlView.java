package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PositionPnl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionPnlView(
        UUID accountId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal marketPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal pnlPercent,
        Instant quoteAsOf,
        String source,
        boolean mock,
        boolean cached,
        boolean stale,
        boolean available) {

    static PositionPnlView from(PositionPnl item) {
        return new PositionPnlView(
                item.accountId(),
                item.ticker(),
                PnlDecimal.api(item.quantity()),
                PnlDecimal.api(item.averageCost()),
                PnlDecimal.api(item.costBasis()),
                PnlDecimal.api(item.marketPrice()),
                PnlDecimal.api(item.marketValue()),
                PnlDecimal.api(item.unrealizedPnl()),
                PnlDecimal.api(item.pnlPercent()),
                item.quoteAsOf(),
                item.source(),
                item.mock(),
                item.cached(),
                item.stale(),
                item.available());
    }
}
