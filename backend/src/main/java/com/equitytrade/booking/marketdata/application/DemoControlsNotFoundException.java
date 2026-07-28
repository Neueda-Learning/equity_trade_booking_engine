package com.equitytrade.booking.marketdata.application;

public class DemoControlsNotFoundException extends RuntimeException {

    public DemoControlsNotFoundException() {
        super("Demo market data controls are disabled");
    }
}
