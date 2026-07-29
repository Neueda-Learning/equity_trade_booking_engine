package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.DailyMarketPrice;
import com.equitytrade.booking.marketdata.domain.HistoricalMarketDataProvider;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeterministicMockHistoricalMarketDataProvider
        implements HistoricalMarketDataProvider {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "AAPL", new BigDecimal("195.25"),
            "MSFT", new BigDecimal("425.40"),
            "NVDA", new BigDecimal("138.75"),
            "GOOGL", new BigDecimal("184.30"),
            "AMZN", new BigDecimal("219.10"));

    @Override
    public List<DailyMarketPrice> fetchDailyCloses(
            String ticker,
            LocalDate fromInclusive,
            LocalDate toInclusive) {
        if (fromInclusive.isAfter(toInclusive)) {
            throw new IllegalArgumentException(
                    "Historical price start must not follow end");
        }
        List<DailyMarketPrice> prices = new ArrayList<>();
        for (LocalDate date = fromInclusive;
                !date.isAfter(toInclusive);
                date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            prices.add(new DailyMarketPrice(
                    ticker,
                    date,
                    price(ticker, date),
                    "MOCK",
                    true));
        }
        return List.copyOf(prices);
    }

    private BigDecimal price(String ticker, LocalDate date) {
        BigDecimal base = BASE_PRICES.getOrDefault(
                ticker,
                BigDecimal.valueOf(
                        2_000L + Math.floorMod(ticker.hashCode(), 48_000),
                        2));
        long seed = 31L * ticker.hashCode() + date.toEpochDay();
        int basisPoints = Math.floorMod(Long.hashCode(seed), 1_001) - 500;
        return base.multiply(
                        BigDecimal.valueOf(10_000L + basisPoints, 4),
                        MATH_CONTEXT)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }
}
