package com.equitytrade.booking.trade.application;

public interface TradeInstrumentValidator {

    String requireSupported(String ticker);
}
