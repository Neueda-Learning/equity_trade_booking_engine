package com.equitytrade.booking.marketdata.infrastructure.scheduling;

import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.MarketQuoteView;
import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderState;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "market-data.background-refresh-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class FinnhubRedisRefreshScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FinnhubRedisRefreshScheduler.class);

    private final MarketDataCache cache;
    private final MarketDataApplicationService marketDataService;
    private final MarketDataProviderState providerState;

    public FinnhubRedisRefreshScheduler(
            MarketDataCache cache,
            @Qualifier("backgroundMarketDataApplicationService")
                    MarketDataApplicationService marketDataService,
            MarketDataProviderState providerState) {
        this.cache = cache;
        this.marketDataService = marketDataService;
        this.providerState = providerState;
    }

    @Scheduled(
            fixedDelayString =
                    "${market-data.background-refresh-interval:10s}",
            initialDelayString =
                    "${market-data.background-refresh-interval:10s}")
    public void refreshCachedQuotes() {
        MarketDataProviderStatus status = providerState.status();
        if (!"FINNHUB".equals(status.provider())
                || !status.configured()) {
            return;
        }

        for (String ticker : cache.tickers()) {
            try {
                MarketQuoteView refreshed = marketDataService.refresh(ticker);
                if (refreshed.stale()
                        && providerUnavailable()) {
                    break;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Background Finnhub refresh failed for {} ({})",
                        ticker,
                        exception.getClass().getSimpleName());
                if (providerUnavailable()) {
                    break;
                }
            }
        }
    }

    private boolean providerUnavailable() {
        MarketDataFailureCategory category =
                providerState.status().lastFailureCategory();
        return category != null
                && category != MarketDataFailureCategory.NOT_FOUND;
    }
}
