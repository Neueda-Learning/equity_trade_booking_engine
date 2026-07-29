package com.equitytrade.booking.marketdata.domain;

import java.time.LocalDate;
import java.util.List;

public interface HistoricalMarketDataProvider {

    List<DailyMarketPrice> fetchDailyCloses(
            String ticker,
            LocalDate fromInclusive,
            LocalDate toInclusive);
}
