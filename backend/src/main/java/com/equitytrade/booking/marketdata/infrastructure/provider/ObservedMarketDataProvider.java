package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;

public class ObservedMarketDataProvider implements MarketDataProvider {

    private final MarketDataProvider delegate;
    private final MarketDataProviderRuntimeState state;
    private final boolean honorDemoOutage;

    public ObservedMarketDataProvider(
            MarketDataProvider delegate,
            MarketDataProviderRuntimeState state) {
        this(delegate, state, true);
    }

    public ObservedMarketDataProvider(
            MarketDataProvider delegate,
            MarketDataProviderRuntimeState state,
            boolean honorDemoOutage) {
        this.delegate = delegate;
        this.state = state;
        this.honorDemoOutage = honorDemoOutage;
    }

    @Override
    public MarketQuote fetch(String ticker) {
        if (honorDemoOutage
                && state.available()
                && state.outageEnabled()) {
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
