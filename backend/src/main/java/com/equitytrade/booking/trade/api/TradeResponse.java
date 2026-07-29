package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.TradeView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TradeResponse")
public record TradeResponse(
        UUID id,
        UUID accountId,
        String ticker,
        @Schema(allowableValues = {"BUY", "SELL"})
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt,
        @Schema(allowableValues = {"BOOKED", "CANCELLED"})
        String status,
        Instant createdAt,
        @Schema(nullable = true)
        Instant cancelledAt,
        @Schema(
                nullable = true,
                allowableValues = {"CANCELLED", "DELETED", "AMENDED"})
        String cancellationReason,
        @Schema(nullable = true)
        UUID supersedesTradeId) {

    static TradeResponse from(TradeView trade) {
        return new TradeResponse(
                trade.id(),
                trade.accountId(),
                trade.ticker(),
                trade.side(),
                trade.quantity(),
                trade.tradePrice(),
                trade.executedAt(),
                trade.status(),
                trade.createdAt(),
                trade.cancelledAt(),
                trade.cancellationReason(),
                trade.supersedesTradeId());
    }
}
