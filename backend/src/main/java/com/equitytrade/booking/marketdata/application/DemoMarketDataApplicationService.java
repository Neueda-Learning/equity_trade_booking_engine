package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.DemoMarketDataControl;

public class DemoMarketDataApplicationService {

    private final DemoMarketDataControl control;

    public DemoMarketDataApplicationService(
            DemoMarketDataControl control) {
        this.control = control;
    }

    public DemoOutageView status() {
        requireAvailable();
        return view();
    }

    public DemoOutageView enable() {
        requireAvailable();
        control.enableOutage();
        return view();
    }

    public DemoOutageView disable() {
        requireAvailable();
        control.disableOutage();
        return view();
    }

    private void requireAvailable() {
        if (!control.available()) {
            throw new DemoControlsNotFoundException();
        }
    }

    private DemoOutageView view() {
        boolean enabled = control.outageEnabled();
        return new DemoOutageView(
                enabled,
                true,
                enabled
                        ? "DEMO outage enabled; external provider calls will fail."
                        : "DEMO outage disabled; external provider calls are available.");
    }
}
