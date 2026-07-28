package com.equitytrade.booking.marketdata.domain;

public interface MarketDataProvider {

    MarketQuote fetch(String ticker);
}
