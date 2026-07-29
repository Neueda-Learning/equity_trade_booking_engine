package com.equitytrade.booking.pnl.application;

import java.util.List;

public record ValuationHistoryView(
        String range,
        List<ValuationHistoryPointView> items) {
}
