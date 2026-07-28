package com.equitytrade.booking.marketdata.domain;

public class MarketDataProviderException extends RuntimeException {

    private final MarketDataFailureCategory category;

    public MarketDataProviderException(String message) {
        this(MarketDataFailureCategory.UNKNOWN, message);
    }

    public MarketDataProviderException(String message, Throwable cause) {
        this(MarketDataFailureCategory.UNKNOWN, message, cause);
    }

    public MarketDataProviderException(
            MarketDataFailureCategory category,
            String message) {
        super(message);
        this.category = category;
    }

    public MarketDataProviderException(
            MarketDataFailureCategory category,
            String message,
            Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public MarketDataFailureCategory category() {
        return category;
    }

    public boolean retryable() {
        return category == MarketDataFailureCategory.TIMEOUT
                || category == MarketDataFailureCategory.CONNECTION
                || category == MarketDataFailureCategory.SERVER_ERROR;
    }
}
