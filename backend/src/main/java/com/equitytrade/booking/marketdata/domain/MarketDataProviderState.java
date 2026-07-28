package com.equitytrade.booking.marketdata.domain;

import java.time.Instant;

public interface MarketDataProviderState {

    MarketDataProviderStatus status();

    boolean hasFailureAfter(String ticker, Instant fetchedAt);
}
