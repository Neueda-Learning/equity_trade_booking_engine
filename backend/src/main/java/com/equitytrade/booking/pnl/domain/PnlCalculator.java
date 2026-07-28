package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PnlCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    private PnlCalculator() {
    }

    public static PnlResult calculate(
            List<PnlPosition> positions,
            Map<String, PnlQuote> quotes) {
        List<PositionPnl> items = positions.stream()
                .filter(position -> position.quantity().signum() != 0)
                .sorted(Comparator.comparing(PnlPosition::ticker))
                .map(position -> calculate(position, quotes.get(
                        position.ticker())))
                .toList();

        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        int priced = 0;
        boolean mock = false;
        boolean stale = false;
        for (PositionPnl item : items) {
            if (!item.available()) {
                continue;
            }
            costBasis = costBasis.add(item.costBasis(), MATH_CONTEXT);
            marketValue = marketValue.add(
                    item.marketValue(),
                    MATH_CONTEXT);
            unrealizedPnl = unrealizedPnl.add(
                    item.unrealizedPnl(),
                    MATH_CONTEXT);
            priced++;
            mock |= item.mock();
            stale |= item.stale();
        }
        int unpriced = items.size() - priced;
        BigDecimal totalPercent = percentage(unrealizedPnl, costBasis);
        return new PnlResult(
                List.copyOf(items),
                new PnlTotals(
                        costBasis,
                        marketValue,
                        unrealizedPnl,
                        totalPercent,
                        items.size(),
                        priced,
                        unpriced,
                        unpriced == 0,
                        mock,
                        stale));
    }

    private static PositionPnl calculate(
            PnlPosition position,
            PnlQuote quote) {
        if (quote == null) {
            return new PositionPnl(
                    position.accountId(),
                    position.ticker(),
                    position.quantity(),
                    position.averageCost(),
                    position.costBasis(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    false);
        }
        BigDecimal marketValue = position.quantity().multiply(
                quote.price(),
                MATH_CONTEXT);
        BigDecimal unrealizedPnl = marketValue.subtract(
                position.costBasis(),
                MATH_CONTEXT);
        return new PositionPnl(
                position.accountId(),
                position.ticker(),
                position.quantity(),
                position.averageCost(),
                position.costBasis(),
                quote.price(),
                marketValue,
                unrealizedPnl,
                percentage(unrealizedPnl, position.costBasis()),
                quote.quoteAsOf(),
                quote.source(),
                quote.mock(),
                quote.cached(),
                quote.stale(),
                true);
    }

    private static BigDecimal percentage(
            BigDecimal numerator,
            BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT);
    }
}
