package com.equitytrade.booking.trade.application;

import java.util.UUID;

public class TradeNotFoundException extends RuntimeException {

    public TradeNotFoundException(UUID id) {
        super("Trade not found: " + id);
    }
}
