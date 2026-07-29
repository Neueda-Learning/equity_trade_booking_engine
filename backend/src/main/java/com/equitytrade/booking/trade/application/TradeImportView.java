package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.TradeImport;
import com.equitytrade.booking.trade.domain.TradeImportStatus;

import java.time.Instant;
import java.util.UUID;

public record TradeImportView(
        UUID importId,
        String firstFileName,
        int rowCount,
        Instant firstImportedAt,
        Instant lastImportedAt,
        int importCount,
        TradeImportStatus status,
        int lastSuccessCount,
        int lastFailureCount) {

    public static TradeImportView from(TradeImport tradeImport) {
        return new TradeImportView(
                tradeImport.id(),
                tradeImport.firstFileName(),
                tradeImport.rowCount(),
                tradeImport.firstImportedAt(),
                tradeImport.lastImportedAt(),
                tradeImport.importCount(),
                tradeImport.status(),
                tradeImport.lastSuccessCount(),
                tradeImport.lastFailureCount());
    }
}
