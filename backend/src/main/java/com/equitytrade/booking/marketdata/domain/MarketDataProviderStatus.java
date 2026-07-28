package com.equitytrade.booking.marketdata.domain;

import java.time.Instant;

public record MarketDataProviderStatus(
        String provider,
        boolean configured,
        boolean demoControlsEnabled,
        boolean demoOutageEnabled,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        MarketDataFailureCategory lastFailureCategory) {
}
