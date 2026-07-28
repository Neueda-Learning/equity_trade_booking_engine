package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionPnl(
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
}
