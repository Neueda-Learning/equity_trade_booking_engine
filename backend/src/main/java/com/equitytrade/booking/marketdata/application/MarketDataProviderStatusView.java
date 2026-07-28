package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketDataProviderStatus;

import java.time.Instant;

public record MarketDataProviderStatusView(
        String provider,
        boolean configured,
        boolean demoControlsEnabled,
        boolean demoOutageEnabled,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastFailureCategory) {

    static MarketDataProviderStatusView from(
            MarketDataProviderStatus status) {
        return new MarketDataProviderStatusView(
                status.provider(),
                status.configured(),
                status.demoControlsEnabled(),
                status.demoOutageEnabled(),
                status.lastSuccessAt(),
                status.lastFailureAt(),
                status.lastFailureCategory() == null
                        ? null
                        : status.lastFailureCategory().name());
    }
}
