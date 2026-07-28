package com.equitytrade.booking.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.net.URI;

@ConfigurationProperties(prefix = "market-data")
public class MarketDataProperties {

    private String provider = "mock";
    private Duration freshTtl = Duration.ofSeconds(60);
    private Duration retentionTtl = Duration.ofHours(24);
    private Duration mockWindow = Duration.ofSeconds(60);
    private URI finnhubBaseUrl = URI.create("https://finnhub.io/api/v1");
    private String finnhubApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(2);
    private int maxAttempts = 2;
    private boolean demoControlsEnabled;

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

    public URI getFinnhubBaseUrl() {
        return finnhubBaseUrl;
    }

    public void setFinnhubBaseUrl(URI finnhubBaseUrl) {
        this.finnhubBaseUrl = finnhubBaseUrl;
    }

    public String getFinnhubApiKey() {
        return finnhubApiKey;
    }

    public void setFinnhubApiKey(String finnhubApiKey) {
        this.finnhubApiKey = finnhubApiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isDemoControlsEnabled() {
        return demoControlsEnabled;
    }

    public void setDemoControlsEnabled(boolean demoControlsEnabled) {
        this.demoControlsEnabled = demoControlsEnabled;
    }
}
