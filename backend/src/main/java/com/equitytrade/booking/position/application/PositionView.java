package com.equitytrade.booking.position.application;

import com.equitytrade.booking.position.domain.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record PositionView(
        UUID accountId,
        String ticker,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis) {

    public static PositionView from(Position position) {
        return new PositionView(
                position.accountId(),
                position.ticker(),
                apiDecimal(position.quantity()),
                apiDecimal(position.averageCost()),
                apiDecimal(position.costBasis()));
    }

    public static PositionView aggregate(
            String ticker,
            BigDecimal quantity,
            BigDecimal costBasis) {
        Position position = new Position(null, ticker, quantity, costBasis);
        return from(position);
    }

    private static BigDecimal apiDecimal(BigDecimal value) {
        BigDecimal rounded = value.setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (rounded.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }
}
