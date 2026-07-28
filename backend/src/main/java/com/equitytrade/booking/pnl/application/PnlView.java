package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PnlResult;

import java.util.List;

public record PnlView(
        List<PositionPnlView> items,
        PnlTotalsView totals) {

    static PnlView from(PnlResult result) {
        return new PnlView(
                result.items().stream()
                        .map(PositionPnlView::from)
                        .toList(),
                PnlTotalsView.from(result.totals()));
    }
}
