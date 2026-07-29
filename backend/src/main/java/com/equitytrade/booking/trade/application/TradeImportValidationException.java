package com.equitytrade.booking.trade.application;

public class TradeImportValidationException extends RuntimeException {

    private final String field;
    private final String reason;

    public TradeImportValidationException(String field, String reason) {
        super(field + " " + reason);
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
