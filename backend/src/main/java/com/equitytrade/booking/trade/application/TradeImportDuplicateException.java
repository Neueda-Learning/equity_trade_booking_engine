package com.equitytrade.booking.trade.application;

public class TradeImportDuplicateException extends RuntimeException {

    private final TradeImportView existingImport;

    public TradeImportDuplicateException(TradeImportView existingImport) {
        super("This CSV table has already been imported.");
        this.existingImport = existingImport;
    }

    public TradeImportView existingImport() {
        return existingImport;
    }
}
