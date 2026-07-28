package com.equitytrade.booking.trade.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateTradeRequest(
        UUID accountId,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt) {
}
