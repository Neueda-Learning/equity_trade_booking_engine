package com.equitytrade.booking.marketdata.infrastructure.redis;

import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class RedisMarketDataCacheTests {

    @Test
    void redisFailureBehavesLikeCacheMissAndNeverBreaksProviderResult() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(
                new RedisConnectionFailureException("Redis unavailable"));
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        RedisMarketDataCache cache = new RedisMarketDataCache(
                template,
                new ObjectMapper().findAndRegisterModules(),
                Duration.ofHours(24));

        assertThat(cache.find("AAPL")).isEmpty();
        assertThatCode(() -> cache.put(quote())).doesNotThrowAnyException();
    }

    private MarketQuote quote() {
        Instant now = Instant.parse("2026-07-28T10:00:00Z");
        return new MarketQuote(
                "AAPL",
                new BigDecimal("195.25"),
                new BigDecimal("193.80"),
                now.minusSeconds(10),
                now,
                "FINNHUB",
                false);
    }
}
