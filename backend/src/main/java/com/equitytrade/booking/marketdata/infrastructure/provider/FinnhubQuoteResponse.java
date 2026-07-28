package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
record FinnhubQuoteResponse(
        BigDecimal c,
        BigDecimal pc,
        Long t) {
}
