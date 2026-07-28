package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PnlQuote(
        String ticker,
        BigDecimal price,
        Instant quoteAsOf,
        String source,
        boolean mock,
        boolean cached,
        boolean stale) {

    public PnlQuote {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(price);
        Objects.requireNonNull(quoteAsOf);
        Objects.requireNonNull(source);
        if (price.signum() <= 0) {
            throw new IllegalArgumentException(
                    "P&L quote price must be positive");
        }
    }
}
