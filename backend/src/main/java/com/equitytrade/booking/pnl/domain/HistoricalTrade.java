package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalTrade(
        UUID id,
        UUID accountId,
        String ticker,
        HistoricalTradeSide side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt,
        Instant operationAt) {

    public HistoricalTrade {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(side);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(tradePrice);
        Objects.requireNonNull(executedAt);
        Objects.requireNonNull(operationAt);
    }
}
