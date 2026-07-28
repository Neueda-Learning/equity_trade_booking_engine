package com.equitytrade.booking.position.domain;

import com.equitytrade.booking.trade.domain.Trade;
import com.equitytrade.booking.trade.domain.TradeSide;
import com.equitytrade.booking.trade.domain.TradeStatus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class PositionCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final Comparator<Trade> REPLAY_ORDER =
            Comparator.comparing(Trade::executedAt)
                    .thenComparing(Trade::createdAt)
                    .thenComparing(Trade::id);

    private PositionCalculator() {
    }

    public static Optional<Position> calculate(Collection<Trade> trades) {
        UUID accountId = null;
        String ticker = null;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;

        for (Trade trade : trades.stream()
                .filter(item -> item.status() == TradeStatus.BOOKED)
                .sorted(REPLAY_ORDER)
                .toList()) {
            if (accountId == null) {
                accountId = trade.accountId();
                ticker = trade.ticker();
            }
            if (trade.side() == TradeSide.BUY) {
                quantity = quantity.add(trade.quantity(), MATH_CONTEXT);
                costBasis = costBasis.add(
                        trade.quantity().multiply(
                                trade.tradePrice(),
                                MATH_CONTEXT),
                        MATH_CONTEXT);
                continue;
            }

            BigDecimal available = quantity;
            if (available.compareTo(trade.quantity()) < 0) {
                throw new NegativePositionException(available);
            }
            BigDecimal averageCost = costBasis.divide(
                    quantity,
                    MATH_CONTEXT);
            quantity = quantity.subtract(trade.quantity(), MATH_CONTEXT);
            costBasis = quantity.signum() == 0
                    ? BigDecimal.ZERO
                    : averageCost.multiply(quantity, MATH_CONTEXT);
        }

        if (accountId == null || quantity.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new Position(
                accountId,
                ticker,
                quantity,
                costBasis));
    }
}
