package com.equitytrade.booking.pnl.application;

import java.util.List;

public record ValuationHistoryView(
        String range,
        String source,
        boolean fallback,
        String failureCategory,
        List<ValuationHistoryPointView> items) {

    public ValuationHistoryView(
            String range,
            List<ValuationHistoryPointView> items) {
        this(range, "PROVIDER", false, null, items);
    }
}
