package com.equitytrade.booking.pnl.domain;

import java.util.List;

public record PnlResult(
        List<PositionPnl> items,
        PnlTotals totals) {
}
