package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record FinnhubInstrumentSearchResponse(
        Integer count,
        List<Result> result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(
            String description,
            String displaySymbol,
            String symbol,
            String type) {
    }
}
