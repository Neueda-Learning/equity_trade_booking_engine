package com.equitytrade.booking.trade.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookTradeCommand(
        UUID accountId,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt) {
}
