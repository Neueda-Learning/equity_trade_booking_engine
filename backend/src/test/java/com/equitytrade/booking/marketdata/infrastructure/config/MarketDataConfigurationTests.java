package com.equitytrade.booking.marketdata.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataConfigurationTests {

    @Test
    void finnhubRequiresAnApiKey() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider("finnhub");
        properties.setFinnhubApiKey("");

        assertThatThrownBy(() ->
                MarketDataConfiguration.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINNHUB_API_KEY")
                .hasMessageNotContaining("https://");
    }

    @Test
    void attemptsAreStrictlyBounded() {
        MarketDataProperties properties = new MarketDataProperties();
        properties.setMaxAttempts(3);

        assertThatThrownBy(() ->
                MarketDataConfiguration.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be 1 or 2");
    }
}
