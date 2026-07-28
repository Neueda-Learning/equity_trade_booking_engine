package com.equitytrade.booking.marketdata.infrastructure.config;

import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.PositionTickerSource;
import com.equitytrade.booking.marketdata.infrastructure.provider.DeterministicMockMarketDataProvider;
import com.equitytrade.booking.marketdata.infrastructure.redis.RedisMarketDataCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "market-data.provider",
            havingValue = "mock",
            matchIfMissing = true)
    MarketDataProvider mockMarketDataProvider(
            Clock clock,
            MarketDataProperties properties) {
        return new DeterministicMockMarketDataProvider(
                clock,
                properties.getMockWindow());
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
            MarketDataProperties properties) {
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
                properties.getFreshTtl());
    }
}
