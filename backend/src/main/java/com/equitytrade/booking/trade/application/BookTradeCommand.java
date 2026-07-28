package com.equitytrade.booking.trade.application;

import java.math.BigDecimal;
import java.time.Instant;

public record BookTradeCommand(
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt) {
}
