package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.TradeImportView;
import com.equitytrade.booking.trade.domain.TradeImportStatus;

import java.time.Instant;
import java.util.UUID;

public record TradeImportResponse(
        UUID importId,
        String firstFileName,
        int rowCount,
        Instant firstImportedAt,
        Instant lastImportedAt,
        int importCount,
        TradeImportStatus status,
        int lastSuccessCount,
        int lastFailureCount) {

    static TradeImportResponse from(TradeImportView view) {
        return new TradeImportResponse(
                view.importId(),
                view.firstFileName(),
                view.rowCount(),
                view.firstImportedAt(),
                view.lastImportedAt(),
                view.importCount(),
                view.status(),
                view.lastSuccessCount(),
                view.lastFailureCount());
    }
}
