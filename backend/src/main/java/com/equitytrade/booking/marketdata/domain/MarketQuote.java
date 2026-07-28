package com.equitytrade.booking.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record MarketQuote(
        String ticker,
        BigDecimal price,
        BigDecimal previousClose,
        Instant marketTimestamp,
        Instant fetchedAt,
        String source,
        boolean mock) {

    public MarketQuote {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(price);
        Objects.requireNonNull(previousClose);
        Objects.requireNonNull(marketTimestamp);
        Objects.requireNonNull(fetchedAt);
        Objects.requireNonNull(source);
        if (price.signum() <= 0 || previousClose.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Market quote prices must be positive");
        }
    }
}
