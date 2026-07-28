package com.equitytrade.booking.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "market-data")
public class MarketDataProperties {

    private String provider = "mock";
    private Duration freshTtl = Duration.ofSeconds(60);
    private Duration retentionTtl = Duration.ofHours(24);
    private Duration mockWindow = Duration.ofSeconds(60);

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Duration getFreshTtl() {
        return freshTtl;
    }

    public void setFreshTtl(Duration freshTtl) {
        this.freshTtl = freshTtl;
    }

    public Duration getRetentionTtl() {
        return retentionTtl;
    }

    public void setRetentionTtl(Duration retentionTtl) {
        this.retentionTtl = retentionTtl;
    }

    public Duration getMockWindow() {
        return mockWindow;
    }

    public void setMockWindow(Duration mockWindow) {
        this.mockWindow = mockWindow;
    }
}
