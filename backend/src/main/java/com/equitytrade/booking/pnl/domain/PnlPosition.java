package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PnlPosition(
        UUID accountId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis) {

    public PnlPosition {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(averageCost);
        Objects.requireNonNull(costBasis);
    }
}
