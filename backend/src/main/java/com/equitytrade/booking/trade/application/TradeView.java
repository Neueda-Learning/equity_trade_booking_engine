package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeView(
        UUID id,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt,
        String status,
        Instant createdAt) {

    static TradeView from(Trade trade) {
        return new TradeView(
                trade.id(),
                trade.ticker(),
                trade.side().name(),
                trade.quantity(),
                trade.tradePrice(),
                trade.executedAt(),
                trade.status().name(),
                trade.createdAt());
    }
}
