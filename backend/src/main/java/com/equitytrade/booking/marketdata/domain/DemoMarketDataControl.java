package com.equitytrade.booking.marketdata.domain;

public interface DemoMarketDataControl {

    boolean available();

    boolean outageEnabled();

    void enableOutage();

    void disableOutage();
}
