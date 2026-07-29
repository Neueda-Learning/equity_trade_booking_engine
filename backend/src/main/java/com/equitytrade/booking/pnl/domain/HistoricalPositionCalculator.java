package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HistoricalPositionCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final Comparator<HistoricalTrade> REPLAY_ORDER =
            Comparator.comparing(HistoricalTrade::executedAt)
                    .thenComparing(HistoricalTrade::operationAt)
                    .thenComparing(HistoricalTrade::id);

    private HistoricalPositionCalculator() {
    }

    public static List<PnlPosition> calculate(
            List<HistoricalTrade> trades,
            Instant executedBefore,
            boolean aggregateAccounts) {
        Map<PositionKey, List<HistoricalTrade>> grouped =
                new LinkedHashMap<>();
        trades.stream()
                .filter(trade -> trade.executedAt().isBefore(executedBefore))
                .sorted(REPLAY_ORDER)
                .forEach(trade -> grouped.computeIfAbsent(
                                new PositionKey(
                                        trade.accountId(),
                                        trade.ticker()),
                                ignored -> new ArrayList<>())
                        .add(trade));

        List<PnlPosition> accountPositions = grouped.values().stream()
                .map(HistoricalPositionCalculator::calculatePosition)
                .filter(position -> position.quantity().signum() != 0)
                .toList();
        if (!aggregateAccounts) {
            return accountPositions;
        }

        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (PnlPosition position : accountPositions) {
            aggregates.computeIfAbsent(
                            position.ticker(),
                            ignored -> new Aggregate())
                    .add(position);
        }
        return aggregates.entrySet().stream()
                .map(entry -> pnlPosition(
                        null,
                        entry.getKey(),
                        entry.getValue().quantity,
                        entry.getValue().costBasis))
                .toList();
    }

    private static PnlPosition calculatePosition(
            List<HistoricalTrade> trades) {
        HistoricalTrade first = trades.getFirst();
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        for (HistoricalTrade trade : trades) {
            if (trade.side() == HistoricalTradeSide.BUY) {
                quantity = quantity.add(trade.quantity(), MATH_CONTEXT);
                costBasis = costBasis.add(
                        trade.quantity().multiply(
                                trade.tradePrice(),
                                MATH_CONTEXT),
                        MATH_CONTEXT);
                continue;
            }
            if (quantity.compareTo(trade.quantity()) < 0) {
                throw new IllegalStateException(
                        "Historical trade replay produced a negative position");
            }
            BigDecimal averageCost = costBasis.divide(
                    quantity,
                    MATH_CONTEXT);
            quantity = quantity.subtract(
                    trade.quantity(),
                    MATH_CONTEXT);
            costBasis = quantity.signum() == 0
                    ? BigDecimal.ZERO
                    : averageCost.multiply(quantity, MATH_CONTEXT);
        }
        return pnlPosition(
                first.accountId(),
                first.ticker(),
                quantity,
                costBasis);
    }

    private static PnlPosition pnlPosition(
            UUID accountId,
            String ticker,
            BigDecimal quantity,
            BigDecimal costBasis) {
        BigDecimal averageCost = quantity.signum() == 0
                ? BigDecimal.ZERO
                : costBasis.divide(quantity, MATH_CONTEXT);
        return new PnlPosition(
                accountId,
                ticker,
                quantity,
                averageCost,
                costBasis);
    }

    private record PositionKey(UUID accountId, String ticker) {
    }

    private static final class Aggregate {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal costBasis = BigDecimal.ZERO;

        private void add(PnlPosition position) {
            quantity = quantity.add(
                    position.quantity(),
                    MATH_CONTEXT);
            costBasis = costBasis.add(
                    position.costBasis(),
                    MATH_CONTEXT);
        }
    }
}
