package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;

public class HistoricalMarketDataUnavailableException
        extends RuntimeException {

    private final MarketDataFailureCategory failureCategory;

    public HistoricalMarketDataUnavailableException(
            MarketDataFailureCategory failureCategory,
            String message) {
        super(message);
        this.failureCategory = failureCategory;
    }

    public HistoricalMarketDataUnavailableException(
            MarketDataFailureCategory failureCategory,
            String message,
            Throwable cause) {
        super(message, cause);
        this.failureCategory = failureCategory;
    }

    public MarketDataFailureCategory failureCategory() {
        return failureCategory;
    }
}
