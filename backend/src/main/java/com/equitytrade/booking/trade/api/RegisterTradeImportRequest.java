package com.equitytrade.booking.trade.api;

public record RegisterTradeImportRequest(
        String contentHash,
        String fileName,
        int rowCount,
        boolean repeatConfirmed) {
}
