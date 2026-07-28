package com.equitytrade.booking.position.api;

import com.equitytrade.booking.position.application.PositionView;

import java.math.BigDecimal;
import java.util.UUID;

public record PositionResponse(
        UUID accountId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averageCost,
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
