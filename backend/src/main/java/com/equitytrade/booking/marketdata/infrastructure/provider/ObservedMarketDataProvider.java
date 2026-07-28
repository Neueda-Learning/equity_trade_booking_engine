package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;

public class ObservedMarketDataProvider implements MarketDataProvider {

    private final MarketDataProvider delegate;
    private final MarketDataProviderRuntimeState state;

    public ObservedMarketDataProvider(
            MarketDataProvider delegate,
            MarketDataProviderRuntimeState state) {
        this.delegate = delegate;
        this.state = state;
    }

    @Override
    public MarketQuote fetch(String ticker) {
        if (state.available() && state.outageEnabled()) {
            MarketDataProviderException outage =
                    new MarketDataProviderException(
                            MarketDataFailureCategory.DEMO_OUTAGE,
                            "DEMO outage is enabled");
            state.recordFailure(ticker, outage.category());
            throw outage;
        }
        try {
            MarketQuote quote = delegate.fetch(ticker);
            state.recordSuccess(ticker);
            return quote;
        } catch (MarketDataProviderException exception) {
            state.recordFailure(ticker, exception.category());
            throw exception;
        }
    }
}
