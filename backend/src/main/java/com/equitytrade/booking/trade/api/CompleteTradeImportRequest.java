package com.equitytrade.booking.trade.api;

public record CompleteTradeImportRequest(
        int importCount,
        int successCount,
        int failureCount) {
}
