package com.equitytrade.booking.marketdata.domain;

public enum MarketDataFailureCategory {
    TIMEOUT,
    CONNECTION,
    RATE_LIMIT,
    SERVER_ERROR,
    AUTHENTICATION,
    NOT_FOUND,
    CLIENT_ERROR,
    MALFORMED_RESPONSE,
    DEMO_OUTAGE,
    UNKNOWN
}
