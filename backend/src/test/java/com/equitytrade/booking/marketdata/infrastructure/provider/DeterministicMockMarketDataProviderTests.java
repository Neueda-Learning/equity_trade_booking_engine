package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketQuote;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicMockMarketDataProviderTests {

    private static final Instant NOW =
            Instant.parse("2026-07-28T08:30:45Z");

    private final DeterministicMockMarketDataProvider provider =
            new DeterministicMockMarketDataProvider(
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    Duration.ofMinutes(1));

    @Test
    void returnsTheSameKnownQuoteWithinAWindow() {
        MarketQuote first = provider.fetch("AAPL");
        MarketQuote second = provider.fetch("AAPL");

        assertThat(first).isEqualTo(second);
        assertThat(first.price()).isEqualByComparingTo("195.25");
        assertThat(first.previousClose()).isEqualByComparingTo("193.80");
        assertThat(first.marketTimestamp())
                .isEqualTo(Instant.parse("2026-07-28T08:30:00Z"));
        assertThat(first.source()).isEqualTo("MOCK");
        assertThat(first.mock()).isTrue();
    }

    @Test
    void generatesAStableQuoteForAnUnknownValidTicker() {
        MarketQuote first = provider.fetch("ACME");
        MarketQuote second = provider.fetch("ACME");
        MarketQuote other = provider.fetch("OTHER");

        assertThat(first).isEqualTo(second);
        assertThat(first.price()).isNotEqualByComparingTo(other.price());
    }

    @Test
    void generatedPricesArePositiveWithAtMostSixDecimalPlaces() {
        MarketQuote quote = provider.fetch("ZZZZ");

        assertThat(quote.price()).isPositive();
        assertThat(quote.previousClose()).isPositive();
        assertThat(quote.price().scale()).isLessThanOrEqualTo(6);
        assertThat(quote.previousClose().scale()).isLessThanOrEqualTo(6);
    }
}
