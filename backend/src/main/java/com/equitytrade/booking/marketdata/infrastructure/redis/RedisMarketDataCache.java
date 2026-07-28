package com.equitytrade.booking.marketdata.infrastructure.redis;

import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisMarketDataCache implements MarketDataCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisMarketDataCache.class);
    private static final String KEY_PREFIX = "market:quote:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration retentionTtl;

    public RedisMarketDataCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Duration retentionTtl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.retentionTtl = retentionTtl;
    }

    @Override
    public Optional<MarketQuote> find(String ticker) {
        try {
            String json = redisTemplate.opsForValue().get(key(ticker));
            return json == null
                    ? Optional.empty()
                    : Optional.of(
                            objectMapper.readValue(json, MarketQuote.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                    "Market quote cache read failed for {}",
                    ticker,
                    exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(MarketQuote quote) {
        try {
            redisTemplate.opsForValue().set(
                    key(quote.ticker()),
                    objectMapper.writeValueAsString(quote),
                    retentionTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                    "Market quote cache write failed for {}",
                    quote.ticker(),
                    exception);
        }
    }

    public static String key(String ticker) {
        return KEY_PREFIX + ticker;
    }
}
