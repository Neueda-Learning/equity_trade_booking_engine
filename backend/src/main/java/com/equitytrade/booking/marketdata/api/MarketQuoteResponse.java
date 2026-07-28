package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.MarketQuoteView;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuoteResponse(
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

    static MarketQuoteResponse from(MarketQuoteView quote) {
        return new MarketQuoteResponse(
                quote.ticker(),
                quote.price(),
                quote.previousClose(),
                quote.change(),
                quote.changePercent(),
                quote.marketTimestamp(),
                quote.fetchedAt(),
                quote.source(),
                quote.mock(),
                quote.cached(),
                quote.stale());
    }
}
