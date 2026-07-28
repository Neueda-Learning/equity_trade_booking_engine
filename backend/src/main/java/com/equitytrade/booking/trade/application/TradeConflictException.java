package com.equitytrade.booking.trade.application;

public class TradeConflictException extends RuntimeException {

    private final String field;
    private final String reason;

    public TradeConflictException(String field, String reason) {
        super(reason);
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
