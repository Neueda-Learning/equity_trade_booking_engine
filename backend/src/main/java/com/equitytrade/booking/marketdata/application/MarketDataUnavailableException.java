package com.equitytrade.booking.marketdata.application;

public class MarketDataUnavailableException extends RuntimeException {

    private final String ticker;

    public MarketDataUnavailableException(String ticker, Throwable cause) {
        super("Market data is unavailable", cause);
        this.ticker = ticker;
    }

    public String ticker() {
        return ticker;
    }
}
