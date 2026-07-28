package com.equitytrade.booking.marketdata.application;

public class MarketDataValidationException extends RuntimeException {

    private final String field;
    private final String reason;

    public MarketDataValidationException(String field, String reason) {
        super("Invalid market data request");
        this.field = field;
        this.reason = reason;
    }

    public String field() {
        return field;
    }

    public String reason() {
        return reason;
    }
}
