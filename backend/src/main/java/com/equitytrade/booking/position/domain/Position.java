package com.equitytrade.booking.position.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.UUID;

public record Position(
        UUID accountId,
        String ticker,
        BigDecimal quantity,
        BigDecimal costBasis) {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal averageCost() {
        return costBasis.divide(quantity, MATH_CONTEXT);
    }
}
