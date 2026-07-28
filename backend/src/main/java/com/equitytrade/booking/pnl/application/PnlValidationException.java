package com.equitytrade.booking.pnl.application;

public class PnlValidationException extends RuntimeException {

    private final String field;
    private final String reason;

    public PnlValidationException(String field, String reason) {
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
