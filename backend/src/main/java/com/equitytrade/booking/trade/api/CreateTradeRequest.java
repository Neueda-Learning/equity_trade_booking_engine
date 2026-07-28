package com.equitytrade.booking.trade.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "CreateTradeRequest",
        description = "BUY or SELL booking linked to an ACTIVE account",
        example = """
                {
                  "accountId": "bb06cce4-21c1-45ce-9bb4-0ebc6b96326c",
                  "ticker": "AAPL",
                  "side": "BUY",
                  "quantity": 10.000000,
                  "tradePrice": 195.250000,
                  "executedAt": "2026-07-28T10:00:00Z"
                }
                """)
public record CreateTradeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID accountId,
        @Schema(example = "AAPL", pattern = "^[A-Z][A-Z0-9.-]{0,9}$")
        String ticker,
        @Schema(allowableValues = {"BUY", "SELL"}, example = "BUY")
        String side,
        @Schema(example = "10.000000", minimum = "0", exclusiveMinimum = true)
        BigDecimal quantity,
        @Schema(example = "195.250000", minimum = "0", exclusiveMinimum = true)
        BigDecimal tradePrice,
        @Schema(example = "2026-07-28T10:00:00Z")
        Instant executedAt) {
}
