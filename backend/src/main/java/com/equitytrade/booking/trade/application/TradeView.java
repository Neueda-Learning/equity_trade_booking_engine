package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeView(
        UUID id,
        UUID accountId,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt,
        String status,
        Instant createdAt,
        Instant cancelledAt,
        String cancellationReason,
        UUID supersedesTradeId) {

    static TradeView from(Trade trade) {
        return new TradeView(
                trade.id(),
                trade.accountId(),
                trade.ticker(),
                trade.side().name(),
                trade.quantity(),
                trade.tradePrice(),
                trade.executedAt(),
                trade.status().name(),
                trade.createdAt(),
                trade.cancelledAt(),
                trade.cancellationReason() == null
                        ? null
                        : trade.cancellationReason().name(),
                trade.supersedesTradeId());
    }
}
