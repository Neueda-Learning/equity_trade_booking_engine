package com.equitytrade.booking.position.api;

import com.equitytrade.booking.position.application.PositionView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(
        name = "PositionResponse",
        description = "BOOKED position calculated with weighted average cost")
public record PositionResponse(
        @Schema(
                nullable = true,
                description = "Null only for an all-account ticker aggregate")
        UUID accountId,
        @Schema(example = "AAPL")
        String ticker,
        @Schema(example = "6.000000")
        BigDecimal quantity,
        @Schema(example = "195.250000")
        BigDecimal averageCost,
        @Schema(example = "1171.500000")
        BigDecimal costBasis) {

    static PositionResponse from(PositionView position) {
        return new PositionResponse(
                position.accountId(),
                position.ticker(),
                position.quantity(),
                position.averageCost(),
                position.costBasis());
    }
}
