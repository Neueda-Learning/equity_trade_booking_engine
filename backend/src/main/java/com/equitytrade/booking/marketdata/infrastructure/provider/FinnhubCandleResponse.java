package com.equitytrade.booking.marketdata.infrastructure.provider;

import java.math.BigDecimal;
import java.util.List;

record FinnhubCandleResponse(
        List<BigDecimal> c,
        List<Long> t,
        String s) {
}
