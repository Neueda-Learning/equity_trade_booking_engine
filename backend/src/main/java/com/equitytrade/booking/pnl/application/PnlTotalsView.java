package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PnlTotals;

import java.math.BigDecimal;

public record PnlTotalsView(
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

    static PnlTotalsView from(PnlTotals totals) {
        return new PnlTotalsView(
                PnlDecimal.api(totals.totalCostBasis()),
                PnlDecimal.api(totals.totalMarketValue()),
                PnlDecimal.api(totals.totalUnrealizedPnl()),
                PnlDecimal.api(totals.totalPnlPercent()),
                totals.positionCount(),
                totals.pricedPositionCount(),
                totals.unpricedPositionCount(),
                totals.complete(),
                totals.mock(),
                totals.stale());
    }
}
