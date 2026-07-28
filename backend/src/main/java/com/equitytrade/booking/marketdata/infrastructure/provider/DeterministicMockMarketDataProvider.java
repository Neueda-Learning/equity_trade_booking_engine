package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketQuote;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class DeterministicMockMarketDataProvider
        implements MarketDataProvider {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final Map<String, BaseQuote> KNOWN_QUOTES = Map.of(
            "AAPL", new BaseQuote("195.25", "193.80"),
            "MSFT", new BaseQuote("425.40", "421.15"),
            "NVDA", new BaseQuote("138.75", "136.20"),
            "GOOGL", new BaseQuote("184.30", "182.95"),
            "AMZN", new BaseQuote("219.10", "216.75"));

    private final Clock clock;
    private final Duration window;

    public DeterministicMockMarketDataProvider(
            Clock clock,
            Duration window) {
        if (window.toSeconds() <= 0) {
            throw new IllegalArgumentException(
                    "Mock market data window must be at least one second");
        }
        this.clock = clock;
        this.window = window;
    }

    @Override
    public MarketQuote fetch(String ticker) {
        Instant now = clock.instant();
        long windowSeconds = window.toSeconds();
        long windowNumber = Math.floorDiv(now.getEpochSecond(), windowSeconds);
        Instant marketTimestamp = Instant.ofEpochSecond(
                windowNumber * windowSeconds);
        BaseQuote known = KNOWN_QUOTES.get(ticker);
        BigDecimal previousClose;
        BigDecimal price;
        if (known != null) {
            price = decimal(known.price);
            previousClose = decimal(known.previousClose);
        } else {
            previousClose = BigDecimal.valueOf(
                    2_000L + Math.floorMod(ticker.hashCode(), 48_000),
                    2);
            int basisPoints = Math.floorMod(
                    31 * ticker.hashCode() + Long.hashCode(windowNumber),
                    501) - 250;
            price = previousClose.multiply(
                    BigDecimal.valueOf(10_000L + basisPoints, 4),
                    MATH_CONTEXT);
        }
        return new MarketQuote(
                ticker,
                apiDecimal(price),
                apiDecimal(previousClose),
                marketTimestamp,
                now,
                "MOCK",
                true);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal apiDecimal(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private record BaseQuote(String price, String previousClose) {
    }
}
