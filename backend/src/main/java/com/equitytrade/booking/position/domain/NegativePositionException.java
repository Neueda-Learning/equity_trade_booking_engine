package com.equitytrade.booking.position.domain;

import java.math.BigDecimal;

public class NegativePositionException extends RuntimeException {

    private final BigDecimal available;

    public NegativePositionException(BigDecimal available) {
        super("Trade sequence would create a negative position");
        this.available = available;
    }

    public BigDecimal available() {
        return available;
    }
}
