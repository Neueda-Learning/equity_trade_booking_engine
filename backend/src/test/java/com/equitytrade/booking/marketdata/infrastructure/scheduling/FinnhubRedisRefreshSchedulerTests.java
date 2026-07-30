package com.equitytrade.booking.marketdata.infrastructure.scheduling;

import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.MarketQuoteView;
import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderState;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinnhubRedisRefreshSchedulerTests {

    private final MarketDataCache cache = mock(MarketDataCache.class);
    private final MarketDataApplicationService service =
            mock(MarketDataApplicationService.class);
    private final MarketDataProviderState providerState =
            mock(MarketDataProviderState.class);
    private final FinnhubRedisRefreshScheduler scheduler =
            new FinnhubRedisRefreshScheduler(
                    cache,
                    service,
                    providerState);

    @Test
    void refreshesEveryTickerAlreadyPresentInRedis() {
        when(providerState.status()).thenReturn(status("FINNHUB", true));
        when(cache.tickers()).thenReturn(List.of("AAPL", "MSFT"));
        when(service.refresh("AAPL")).thenReturn(quote("AAPL", false));
        when(service.refresh("MSFT")).thenReturn(quote("MSFT", false));

        scheduler.refreshCachedQuotes();

        var ordered = inOrder(service);
        ordered.verify(service).refresh("AAPL");
        ordered.verify(service).refresh("MSFT");
    }

    @Test
    void skipsRefreshWhenProviderIsNotUsableFinnhub() {
        List.of(
                status("MOCK", true),
                status("FINNHUB", false))
                .forEach(status -> {
                    when(providerState.status()).thenReturn(status);
                    scheduler.refreshCachedQuotes();
                });

        verifyNoInteractions(cache, service);
    }

    @Test
    void keepsRefreshingRedisDuringForegroundDemoOutage() {
        MarketDataProviderStatus demoOutage =
                new MarketDataProviderStatus(
                        "FINNHUB",
                        true,
                        true,
                        true,
                        null,
                        null,
                        MarketDataFailureCategory.DEMO_OUTAGE);
        when(providerState.status()).thenReturn(demoOutage);
        when(cache.tickers()).thenReturn(List.of("AAPL"));
        when(service.refresh("AAPL")).thenReturn(quote("AAPL", false));

        scheduler.refreshCachedQuotes();

        verify(service).refresh("AAPL");
    }

    @Test
    void stopsCurrentCycleWhenFinnhubBecomesUnavailable() {
        MarketDataProviderStatus available = status("FINNHUB", true);
        MarketDataProviderStatus failed = new MarketDataProviderStatus(
                "FINNHUB",
                true,
                false,
                false,
                null,
                Instant.parse("2026-07-30T10:00:00Z"),
                MarketDataFailureCategory.SERVER_ERROR);
        when(providerState.status()).thenReturn(available, failed);
        when(cache.tickers()).thenReturn(List.of("AAPL", "MSFT"));
        when(service.refresh("AAPL")).thenReturn(quote("AAPL", true));

        scheduler.refreshCachedQuotes();

        verify(service).refresh("AAPL");
        verify(service, never()).refresh("MSFT");
    }

    private MarketDataProviderStatus status(
            String provider,
            boolean configured) {
        return new MarketDataProviderStatus(
                provider,
                configured,
                false,
                false,
                null,
                null,
                null);
    }

    private MarketQuoteView quote(String ticker, boolean stale) {
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        return new MarketQuoteView(
                ticker,
                new BigDecimal("100"),
                new BigDecimal("99"),
                new BigDecimal("1"),
                new BigDecimal("1.010101"),
                now,
                now,
                "FINNHUB",
                false,
                stale,
                stale);
    }
}
