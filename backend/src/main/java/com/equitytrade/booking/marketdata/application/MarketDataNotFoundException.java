package com.equitytrade.booking.marketdata.application;

public class MarketDataNotFoundException extends RuntimeException {

    private final String ticker;

    public MarketDataNotFoundException(String ticker, Throwable cause) {
        super("Market quote was not found", cause);
        this.ticker = ticker;
    }

    public String ticker() {
        return ticker;
    }
}
