package com.equitytrade.booking.marketdata.infrastructure.config;

import com.equitytrade.booking.marketdata.application.DemoMarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.MarketDataProviderStatusService;
import com.equitytrade.booking.marketdata.domain.DemoMarketDataControl;
import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderState;
import com.equitytrade.booking.marketdata.domain.PositionTickerSource;
import com.equitytrade.booking.marketdata.infrastructure.provider.DeterministicMockMarketDataProvider;
import com.equitytrade.booking.marketdata.infrastructure.provider.FinnhubMarketDataProvider;
import com.equitytrade.booking.marketdata.infrastructure.provider.MarketDataProviderRuntimeState;
import com.equitytrade.booking.marketdata.infrastructure.provider.ObservedMarketDataProvider;
import com.equitytrade.booking.marketdata.infrastructure.redis.RedisMarketDataCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.net.http.HttpClient;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfiguration {

    @Bean
    MarketDataProviderRuntimeState marketDataProviderRuntimeState(
            Clock clock,
            MarketDataProperties properties) {
        String provider = provider(properties);
        boolean configured = !"FINNHUB".equals(provider)
                || !properties.getFinnhubApiKey().isBlank();
        boolean demoControlsEnabled =
                "FINNHUB".equals(provider)
                        && properties.isDemoControlsEnabled();
        return new MarketDataProviderRuntimeState(
                provider,
                configured,
                demoControlsEnabled,
                clock);
    }

    @Bean
    MarketDataProvider marketDataProvider(
            Clock clock,
            ObjectMapper objectMapper,
            MarketDataProperties properties,
            MarketDataProviderRuntimeState runtimeState) {
        validate(properties);
        MarketDataProvider provider = switch (provider(properties)) {
            case "MOCK" -> new DeterministicMockMarketDataProvider(
                    clock,
                    properties.getMockWindow());
            case "FINNHUB" -> new FinnhubMarketDataProvider(
                    HttpClient.newBuilder()
                            .connectTimeout(properties.getConnectTimeout())
                            .build(),
                    objectMapper,
                    clock,
                    properties.getFinnhubBaseUrl(),
                    properties.getFinnhubApiKey(),
                    properties.getReadTimeout(),
                    properties.getMaxAttempts());
            default -> throw new IllegalStateException(
                    "MARKET_DATA_PROVIDER must be mock or finnhub");
        };
        return new ObservedMarketDataProvider(provider, runtimeState);
    }

    @Bean
    MarketDataCache marketDataCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MarketDataProperties properties) {
        return new RedisMarketDataCache(
                redisTemplate,
                objectMapper,
                properties.getRetentionTtl());
    }

    @Bean
    MarketDataApplicationService marketDataApplicationService(
            MarketDataProvider provider,
            MarketDataCache cache,
            PositionTickerSource positionTickerSource,
            Clock clock,
            MarketDataProperties properties,
            MarketDataProviderState providerState) {
        if (properties.getFreshTtl().isNegative()
                || properties.getFreshTtl().isZero()
                || properties.getRetentionTtl()
                        .compareTo(properties.getFreshTtl()) <= 0) {
            throw new IllegalStateException(
                    "Market data retention TTL must exceed fresh TTL");
        }
        return new MarketDataApplicationService(
                provider,
                cache,
                positionTickerSource,
                clock,
                properties.getFreshTtl(),
                providerState);
    }

    @Bean
    MarketDataProviderStatusService marketDataProviderStatusService(
            MarketDataProviderState providerState) {
        return new MarketDataProviderStatusService(providerState);
    }

    @Bean
    DemoMarketDataApplicationService demoMarketDataApplicationService(
            DemoMarketDataControl control) {
        return new DemoMarketDataApplicationService(control);
    }

    private static String provider(MarketDataProperties properties) {
        return properties.getProvider()
                .strip()
                .toUpperCase(Locale.ROOT);
    }

    static void validate(MarketDataProperties properties) {
        String provider = provider(properties);
        if ("FINNHUB".equals(provider)
                && properties.getFinnhubApiKey().isBlank()) {
            throw new IllegalStateException(
                    "FINNHUB_API_KEY must be configured when "
                            + "MARKET_DATA_PROVIDER=finnhub");
        }
        if (properties.getFinnhubBaseUrl() == null
                || (!"http".equalsIgnoreCase(
                                properties.getFinnhubBaseUrl().getScheme())
                        && !"https".equalsIgnoreCase(
                                properties.getFinnhubBaseUrl().getScheme()))) {
            throw new IllegalStateException(
                    "FINNHUB_BASE_URL must use http or https");
        }
        if (properties.getConnectTimeout().isNegative()
                || properties.getConnectTimeout().isZero()
                || properties.getReadTimeout().isNegative()
                || properties.getReadTimeout().isZero()) {
            throw new IllegalStateException(
                    "Finnhub timeouts must be positive");
        }
        if (properties.getMaxAttempts() < 1
                || properties.getMaxAttempts() > 2) {
            throw new IllegalStateException(
                    "MARKET_DATA_MAX_ATTEMPTS must be 1 or 2");
        }
    }
}
