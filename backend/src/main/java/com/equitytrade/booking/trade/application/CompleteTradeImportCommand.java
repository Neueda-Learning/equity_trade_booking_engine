package com.equitytrade.booking.trade.application;

public record CompleteTradeImportCommand(
        int importCount,
        int successCount,
        int failureCount) {
}
