package com.equitytrade.booking.trade.application;

import java.util.UUID;

public class TradeImportNotFoundException extends RuntimeException {

    public TradeImportNotFoundException(UUID id) {
        super("Trade import " + id + " does not exist.");
    }
}
