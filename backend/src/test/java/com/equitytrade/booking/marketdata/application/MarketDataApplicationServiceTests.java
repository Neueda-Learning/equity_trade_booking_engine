package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.equitytrade.booking.marketdata.domain.MarketQuoteSnapshot;
import com.equitytrade.booking.marketdata.domain.PositionTickerSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataApplicationServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-07-28T08:00:00Z");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final InMemoryCache cache = new InMemoryCache();
    private final StubProvider provider = new StubProvider();
    private final PositionTickerSource tickers = accountId ->
            List.of("MSFT", "AAPL");
    private final MarketDataApplicationService service =
            new MarketDataApplicationService(
                    provider,
                    cache,
                    tickers,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    Duration.ofSeconds(60));

    @Test
    void cacheMissFetchesAndWritesQuote() {
        MarketQuoteView result = service.quote(" aapl ");

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.cached()).isFalse();
        assertThat(result.stale()).isFalse();
        assertThat(provider.calls).hasValue(1);
        assertThat(cache.find("AAPL")).contains(provider.quote);
    }

    @Test
    void freshCacheHitDoesNotCallProvider() {
        cache.put(quote("AAPL", NOW.minusSeconds(30)));

        MarketQuoteView result = service.quote("AAPL");

        assertThat(result.cached()).isTrue();
        assertThat(result.stale()).isFalse();
        assertThat(provider.calls).hasValue(0);
    }

    @Test
    void staleCacheCallsProviderAndReplacesValue() {
        cache.put(quote("AAPL", NOW.minusSeconds(61)));

        MarketQuoteView result = service.quote("AAPL");

        assertThat(result.cached()).isFalse();
        assertThat(provider.calls).hasValue(1);
        assertThat(cache.find("AAPL")).contains(provider.quote);
    }

    @Test
    void providerFailureReturnsStaleCache() {
        MarketQuote stale = quote("AAPL", NOW.minusSeconds(61));
        cache.put(stale);
        provider.fail = true;

        MarketQuoteView result = service.quote("AAPL");

        assertThat(result.cached()).isTrue();
        assertThat(result.stale()).isTrue();
        assertThat(result.price()).isEqualByComparingTo(stale.price());
    }

    @Test
    void providerFailureWithoutCacheIsUnavailable() {
        provider.fail = true;

        assertThatThrownBy(() -> service.quote("AAPL"))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void notFoundWithoutCacheIsDistinctAndNeverFallsBackToMock() {
        provider.failureCategory = MarketDataFailureCategory.NOT_FOUND;

        assertThatThrownBy(() -> service.quote("AAPL"))
                .isInstanceOf(MarketDataNotFoundException.class);
        assertThat(cache.find("AAPL")).isEmpty();
        assertThat(provider.calls).hasValue(1);
    }

    @Test
    void validatesTickerAndUsesAccountScopedPositionTickers() {
        assertThatThrownBy(() -> service.quote("bad ticker"))
                .isInstanceOf(MarketDataValidationException.class)
                .extracting("field")
                .isEqualTo("ticker");

        List<MarketQuoteView> results = service.quotes(ACCOUNT_ID);
        assertThat(results).extracting(MarketQuoteView::ticker)
                .containsExactly("AAPL", "MSFT");
    }

    @Test
    void refreshAlwaysCallsProviderEvenForFreshCache() {
        cache.put(quote("AAPL", NOW.minusSeconds(1)));

        MarketQuoteView result = service.refresh("AAPL");

        assertThat(result.cached()).isFalse();
        assertThat(provider.calls).hasValue(1);
    }

    @Test
    void everySuccessfulProviderQuoteIsPersistedButCacheHitsAreNotDuplicated() {
        List<MarketQuoteSnapshot> snapshots = new ArrayList<>();
        MarketDataApplicationService persistentService =
                new MarketDataApplicationService(
                        provider,
                        cache,
                        tickers,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofSeconds(60),
                        null,
                        snapshot -> {
                            snapshots.add(snapshot);
                            return snapshot;
                        });

        persistentService.quote("AAPL");
        persistentService.quote("AAPL");
        persistentService.refresh("AAPL");

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots)
                .extracting(MarketQuoteSnapshot::ticker)
                .containsOnly("AAPL");
        assertThat(snapshots)
                .extracting(MarketQuoteSnapshot::persistedAt)
                .containsOnly(NOW);
    }

    private static MarketQuote quote(String ticker, Instant fetchedAt) {
        return new MarketQuote(
                ticker,
                new BigDecimal("100.25"),
                new BigDecimal("99.75"),
                fetchedAt,
                fetchedAt,
                "MOCK",
                true);
    }

    private static final class InMemoryCache implements MarketDataCache {
        private final Map<String, MarketQuote> values = new HashMap<>();

        @Override
        public Optional<MarketQuote> find(String ticker) {
            return Optional.ofNullable(values.get(ticker));
        }

        @Override
        public void put(MarketQuote quote) {
            values.put(quote.ticker(), quote);
        }

        @Override
        public List<String> tickers() {
            return values.keySet().stream().sorted().toList();
        }
    }

    private static final class StubProvider implements MarketDataProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final MarketQuote quote = quote("AAPL", NOW);
        private boolean fail;
        private MarketDataFailureCategory failureCategory;

        @Override
        public MarketQuote fetch(String ticker) {
            calls.incrementAndGet();
            if (fail || failureCategory != null) {
                throw new MarketDataProviderException(
                        failureCategory == null
                                ? MarketDataFailureCategory.UNKNOWN
                                : failureCategory,
                        "provider unavailable");
            }
            return ticker.equals(quote.ticker())
                    ? quote
                    : quote(ticker, NOW);
        }
    }
}
