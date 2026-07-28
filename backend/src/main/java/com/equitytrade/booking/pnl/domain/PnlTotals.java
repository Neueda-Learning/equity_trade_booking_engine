package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;

public record PnlTotals(
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalPnlPercent,
        int positionCount,
        int pricedPositionCount,
        int unpricedPositionCount,
        boolean complete,
        boolean mock,
        boolean stale) {
}
