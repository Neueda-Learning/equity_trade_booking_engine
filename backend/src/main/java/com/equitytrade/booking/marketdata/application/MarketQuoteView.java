package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketQuote;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

public record MarketQuoteView(
        String ticker,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        Instant marketTimestamp,
        Instant fetchedAt,
        String source,
        boolean mock,
        boolean cached,
        boolean stale) {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public static MarketQuoteView from(
            MarketQuote quote,
            boolean cached,
            boolean stale) {
        BigDecimal change = quote.price().subtract(
                quote.previousClose(),
                MATH_CONTEXT);
        BigDecimal changePercent = change
                .divide(quote.previousClose(), MATH_CONTEXT)
                .multiply(BigDecimal.valueOf(100), MATH_CONTEXT);
        return new MarketQuoteView(
                quote.ticker(),
                apiDecimal(quote.price()),
                apiDecimal(quote.previousClose()),
                apiDecimal(change),
                apiDecimal(changePercent),
                quote.marketTimestamp(),
                quote.fetchedAt(),
                quote.source(),
                quote.mock(),
                cached,
                stale);
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
