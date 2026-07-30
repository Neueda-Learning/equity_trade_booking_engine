package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservedMarketDataProviderTests {

    private static final Instant NOW =
            Instant.parse("2026-07-28T10:00:00Z");

    @Test
    void recordsSuccessAndClearsTickerFailure() {
        AtomicInteger calls = new AtomicInteger();
        MarketDataProviderRuntimeState state = state(true);
        state.recordFailure(
                "AAPL",
                MarketDataFailureCategory.SERVER_ERROR);
        ObservedMarketDataProvider provider =
                new ObservedMarketDataProvider(
                        ticker -> {
                            calls.incrementAndGet();
                            return quote(ticker);
                        },
                        state);

        provider.fetch("AAPL");

        assertThat(calls).hasValue(1);
        assertThat(state.status().lastSuccessAt()).isEqualTo(NOW);
        assertThat(state.hasFailureAfter("AAPL", Instant.EPOCH))
                .isFalse();
    }

    @Test
    void demoOutageNeverCallsDelegateAndIsClearlyCategorized() {
        AtomicInteger calls = new AtomicInteger();
        MarketDataProviderRuntimeState state = state(true);
        state.enableOutage();
        ObservedMarketDataProvider provider =
                new ObservedMarketDataProvider(
                        ticker -> {
                            calls.incrementAndGet();
                            return quote(ticker);
                        },
                        state);

        assertThatThrownBy(() -> provider.fetch("AAPL"))
                .isInstanceOf(MarketDataProviderException.class)
                .extracting(exception ->
                        ((MarketDataProviderException) exception).category())
                .isEqualTo(MarketDataFailureCategory.DEMO_OUTAGE);
        assertThat(calls).hasValue(0);
        assertThat(state.status().demoOutageEnabled()).isTrue();
        assertThat(state.status().lastFailureCategory())
                .isEqualTo(MarketDataFailureCategory.DEMO_OUTAGE);

        state.disableOutage();
        assertThat(provider.fetch("AAPL")).isEqualTo(quote("AAPL"));
    }

    @Test
    void backgroundProviderBypassesForegroundDemoOutage() {
        AtomicInteger calls = new AtomicInteger();
        MarketDataProviderRuntimeState state = state(true);
        state.enableOutage();
        ObservedMarketDataProvider provider =
                new ObservedMarketDataProvider(
                        ticker -> {
                            calls.incrementAndGet();
                            return quote(ticker);
                        },
                        state,
                        false);

        assertThat(provider.fetch("AAPL")).isEqualTo(quote("AAPL"));
        assertThat(calls).hasValue(1);
        assertThat(state.status().demoOutageEnabled()).isTrue();
        assertThat(state.status().lastSuccessAt()).isEqualTo(NOW);
    }

    private MarketDataProviderRuntimeState state(boolean demoControls) {
        return new MarketDataProviderRuntimeState(
                "FINNHUB",
                true,
                demoControls,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MarketQuote quote(String ticker) {
        return new MarketQuote(
                ticker,
                new BigDecimal("195.25"),
                new BigDecimal("193.80"),
                NOW.minusSeconds(30),
                NOW,
                "FINNHUB",
                false);
    }
}
