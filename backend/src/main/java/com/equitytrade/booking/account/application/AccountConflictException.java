package com.equitytrade.booking.account.application;

public class AccountConflictException extends RuntimeException {

    private final String field;
    private final String reason;

    public AccountConflictException(String field, String reason) {
        super("Account conflict");
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
