package com.equitytrade.booking.marketdata.domain;

import java.util.Objects;

public record InstrumentSearchResult(
        String ticker,
        String name,
        String exchange,
        String type) {

    public InstrumentSearchResult {
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(name);
        Objects.requireNonNull(exchange);
        Objects.requireNonNull(type);
    }
}
