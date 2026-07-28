package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.equitytrade.booking.marketdata.domain.MarketTicker;
import com.equitytrade.booking.marketdata.domain.PositionTickerSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MarketDataApplicationService {

    private final MarketDataProvider provider;
    private final MarketDataCache cache;
    private final PositionTickerSource positionTickerSource;
    private final Clock clock;
    private final Duration freshTtl;

    public MarketDataApplicationService(
            MarketDataProvider provider,
            MarketDataCache cache,
            PositionTickerSource positionTickerSource,
            Clock clock,
            Duration freshTtl) {
        this.provider = provider;
        this.cache = cache;
        this.positionTickerSource = positionTickerSource;
        this.clock = clock;
        this.freshTtl = freshTtl;
    }

    public MarketQuoteView quote(String rawTicker) {
        return resolve(normalize(rawTicker), false);
    }

    public MarketQuoteView refresh(String rawTicker) {
        return resolve(normalize(rawTicker), true);
    }

    public List<MarketQuoteView> quotes(UUID accountId) {
        return positionTickerSource.findTickers(accountId).stream()
                .sorted()
                .map(ticker -> resolve(ticker, false))
                .toList();
    }

    private MarketQuoteView resolve(String ticker, boolean forceRefresh) {
        Optional<MarketQuote> cachedQuote = cache.find(ticker);
        if (!forceRefresh
                && cachedQuote.filter(this::isFresh).isPresent()) {
            return MarketQuoteView.from(cachedQuote.orElseThrow(), true, false);
        }
        try {
            MarketQuote fetched = provider.fetch(ticker);
            cache.put(fetched);
            return MarketQuoteView.from(fetched, false, false);
        } catch (MarketDataProviderException exception) {
            return cachedQuote
                    .map(quote -> MarketQuoteView.from(quote, true, true))
                    .orElseThrow(() ->
                            new MarketDataUnavailableException(
                                    ticker,
                                    exception));
        }
    }

    private boolean isFresh(MarketQuote quote) {
        Instant oldestFreshValue = clock.instant().minus(freshTtl);
        return !quote.fetchedAt().isBefore(oldestFreshValue);
    }

    private String normalize(String rawTicker) {
        try {
            return MarketTicker.normalize(rawTicker);
        } catch (IllegalArgumentException exception) {
            throw new MarketDataValidationException(
                    "ticker",
                    exception.getMessage());
        }
    }
}
