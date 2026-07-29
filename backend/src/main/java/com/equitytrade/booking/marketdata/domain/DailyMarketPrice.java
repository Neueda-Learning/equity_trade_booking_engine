package com.equitytrade.booking.marketdata.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record DailyMarketPrice(
        String ticker,
        LocalDate tradingDate,
        BigDecimal close,
        String source,
        boolean mock) {

    public DailyMarketPrice {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(tradingDate);
        Objects.requireNonNull(close);
        Objects.requireNonNull(source);
        if (close.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Daily market close must be positive");
        }
    }
}
