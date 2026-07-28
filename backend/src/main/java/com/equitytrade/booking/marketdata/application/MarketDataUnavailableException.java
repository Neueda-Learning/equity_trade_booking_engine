package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;

public class MarketDataUnavailableException extends RuntimeException {

    private final String ticker;
    private final MarketDataFailureCategory failureCategory;

    public MarketDataUnavailableException(String ticker, Throwable cause) {
        super("Market data is unavailable", cause);
        this.ticker = ticker;
        this.failureCategory = cause instanceof MarketDataProviderException failure
                ? failure.category()
                : MarketDataFailureCategory.UNKNOWN;
    }

    public String ticker() {
        return ticker;
    }

    public MarketDataFailureCategory failureCategory() {
        return failureCategory;
    }
}
