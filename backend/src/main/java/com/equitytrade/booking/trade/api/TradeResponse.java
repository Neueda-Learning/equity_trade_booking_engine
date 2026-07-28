package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.TradeView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt,
        String status,
        Instant createdAt) {

    static TradeResponse from(TradeView trade) {
        return new TradeResponse(
                trade.id(),
                trade.ticker(),
                trade.side(),
                trade.quantity(),
                trade.tradePrice(),
                trade.executedAt(),
                trade.status(),
                trade.createdAt());
    }
}
