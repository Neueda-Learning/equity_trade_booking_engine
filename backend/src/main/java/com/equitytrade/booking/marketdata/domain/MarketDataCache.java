package com.equitytrade.booking.marketdata.domain;

import java.util.List;
import java.util.Optional;

public interface MarketDataCache {

    Optional<MarketQuote> find(String ticker);

    void put(MarketQuote quote);

    List<String> tickers();
}
