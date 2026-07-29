package com.equitytrade.booking.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketQuoteSnapshot(
        UUID id,
        String ticker,
        BigDecimal price,
        BigDecimal previousClose,
        Instant marketTimestamp,
        Instant fetchedAt,
        String source,
        boolean mock,
        Instant persistedAt) {

    public MarketQuoteSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(price);
        Objects.requireNonNull(previousClose);
        Objects.requireNonNull(marketTimestamp);
        Objects.requireNonNull(fetchedAt);
        Objects.requireNonNull(source);
        Objects.requireNonNull(persistedAt);
    }

    public static MarketQuoteSnapshot capture(
            MarketQuote quote,
            Instant persistedAt) {
        return new MarketQuoteSnapshot(
                UUID.randomUUID(),
                quote.ticker(),
                quote.price(),
                quote.previousClose(),
                quote.marketTimestamp(),
                quote.fetchedAt(),
                quote.source(),
                quote.mock(),
                persistedAt);
    }
}
