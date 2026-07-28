package com.equitytrade.booking.marketdata.application;

public record DemoOutageView(
        boolean enabled,
        boolean demoOnly,
        String message) {
}
