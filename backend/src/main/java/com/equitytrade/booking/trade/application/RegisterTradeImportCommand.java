package com.equitytrade.booking.trade.application;

public record RegisterTradeImportCommand(
        String contentHash,
        String fileName,
        int rowCount,
        boolean repeatConfirmed) {
}
