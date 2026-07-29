package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;

public class InstrumentSearchUnavailableException extends RuntimeException {

    private final MarketDataFailureCategory failureCategory;

    public InstrumentSearchUnavailableException(
            MarketDataFailureCategory failureCategory,
            Throwable cause) {
        super("Instrument search is unavailable", cause);
        this.failureCategory = failureCategory;
    }

    public MarketDataFailureCategory failureCategory() {
        return failureCategory;
    }
}
