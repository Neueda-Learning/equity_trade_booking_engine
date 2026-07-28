package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.DemoMarketDataControl;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderState;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MarketDataProviderRuntimeState
        implements MarketDataProviderState, DemoMarketDataControl {

    private final String provider;
    private final boolean configured;
    private final boolean demoControlsEnabled;
    private final Clock clock;
    private final AtomicBoolean demoOutage = new AtomicBoolean();
    private final AtomicReference<Instant> lastSuccessAt =
            new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt =
            new AtomicReference<>();
    private final AtomicReference<MarketDataFailureCategory>
            lastFailureCategory = new AtomicReference<>();
    private final Map<String, Instant> tickerFailures =
            new ConcurrentHashMap<>();

    public MarketDataProviderRuntimeState(
            String provider,
            boolean configured,
            boolean demoControlsEnabled,
            Clock clock) {
        this.provider = provider;
        this.configured = configured;
        this.demoControlsEnabled = demoControlsEnabled;
        this.clock = clock;
    }

    public void recordSuccess(String ticker) {
        Instant now = clock.instant();
        lastSuccessAt.set(now);
        tickerFailures.remove(ticker);
    }

    public void recordFailure(
            String ticker,
            MarketDataFailureCategory category) {
        Instant now = clock.instant();
        lastFailureAt.set(now);
        lastFailureCategory.set(category);
        tickerFailures.put(ticker, now);
    }

    @Override
    public MarketDataProviderStatus status() {
        return new MarketDataProviderStatus(
                provider,
                configured,
                demoControlsEnabled,
                demoOutage.get(),
                lastSuccessAt.get(),
                lastFailureAt.get(),
                lastFailureCategory.get());
    }

    @Override
    public boolean hasFailureAfter(
            String ticker,
            Instant fetchedAt) {
        Instant failure = tickerFailures.get(ticker);
        return failure != null && !failure.isBefore(fetchedAt);
    }

    @Override
    public boolean available() {
        return demoControlsEnabled;
    }

    @Override
    public boolean outageEnabled() {
        return demoOutage.get();
    }

    @Override
    public void enableOutage() {
        demoOutage.set(true);
    }

    @Override
    public void disableOutage() {
        demoOutage.set(false);
    }
}
