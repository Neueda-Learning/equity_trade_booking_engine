package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.ValuationSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ValuationSnapshotView(
        UUID id,
        String scopeType,
        UUID accountId,
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal unrealizedPnl,
        int positionCount,
        int pricedPositionCount,
        boolean complete,
        boolean mock,
        boolean stale,
        Instant capturedAt) {

    static ValuationSnapshotView from(ValuationSnapshot snapshot) {
        return new ValuationSnapshotView(
                snapshot.id(),
                snapshot.scopeType().name(),
                snapshot.accountId(),
                PnlDecimal.api(snapshot.totalCostBasis()),
                PnlDecimal.api(snapshot.totalMarketValue()),
                PnlDecimal.api(snapshot.unrealizedPnl()),
                snapshot.positionCount(),
                snapshot.pricedPositionCount(),
                snapshot.complete(),
                snapshot.mock(),
                snapshot.stale(),
                snapshot.capturedAt());
    }
}
