package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.InstrumentSearchResult;

public record InstrumentSearchView(
        String ticker,
        String name,
        String exchange,
        String type) {

    static InstrumentSearchView from(InstrumentSearchResult result) {
        return new InstrumentSearchView(
                result.ticker(),
                result.name(),
                result.exchange(),
                result.type());
    }
}
