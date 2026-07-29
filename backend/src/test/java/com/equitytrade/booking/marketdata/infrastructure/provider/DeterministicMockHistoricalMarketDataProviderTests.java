package com.equitytrade.booking.marketdata.infrastructure.provider;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicMockHistoricalMarketDataProviderTests {

    @Test
    void returnsStableWeekdayClosesOnly() {
        DeterministicMockHistoricalMarketDataProvider provider =
                new DeterministicMockHistoricalMarketDataProvider();

        var first = provider.fetchDailyCloses(
                "AAPL",
                LocalDate.parse("2026-07-24"),
                LocalDate.parse("2026-07-27"));
        var second = provider.fetchDailyCloses(
                "AAPL",
                LocalDate.parse("2026-07-24"),
                LocalDate.parse("2026-07-27"));

        assertThat(first).hasSize(2).isEqualTo(second);
        assertThat(first)
                .extracting(price -> price.tradingDate().toString())
                .containsExactly("2026-07-24", "2026-07-27");
    }
}
