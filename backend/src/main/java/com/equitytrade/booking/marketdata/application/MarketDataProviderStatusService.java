package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketDataProviderState;

public class MarketDataProviderStatusService {

    private final MarketDataProviderState providerState;

    public MarketDataProviderStatusService(
            MarketDataProviderState providerState) {
        this.providerState = providerState;
    }

    public MarketDataProviderStatusView get() {
        return MarketDataProviderStatusView.from(providerState.status());
    }
}
