package com.equitytrade.booking.marketdata.infrastructure.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicMockInstrumentSearchProviderTests {

    private final DeterministicMockInstrumentSearchProvider provider =
            new DeterministicMockInstrumentSearchProvider();

    @Test
    void searchesByTickerAndCompanyNameDeterministically() {
        assertThat(provider.search("aapl", 10))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.ticker()).isEqualTo("AAPL");
                    assertThat(result.name()).isEqualTo("APPLE INC");
                    assertThat(result.exchange()).isEqualTo("US");
                    assertThat(result.type()).isEqualTo("Common Stock");
                });
        assertThat(provider.search("apple", 10))
                .extracting(result -> result.ticker())
                .containsExactly("AAPL");
    }

    @Test
    void limitsResultsAndOnlyReturnsSupportedSecurityTypes() {
        assertThat(provider.search("a", 3)).hasSize(3);
        assertThat(provider.search("spy", 10))
                .singleElement()
                .satisfies(result ->
                        assertThat(result.type()).isEqualTo("ETF"));
        assertThat(provider.search("unknown security", 10)).isEmpty();
    }
}
