package com.equitytrade.booking.trade.domain;

import java.util.List;

public class TradeValidationException extends RuntimeException {

    private final List<TradeFieldViolation> violations;

    public TradeValidationException(List<TradeFieldViolation> violations) {
        super("Trade validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<TradeFieldViolation> violations() {
        return violations;
    }
}
