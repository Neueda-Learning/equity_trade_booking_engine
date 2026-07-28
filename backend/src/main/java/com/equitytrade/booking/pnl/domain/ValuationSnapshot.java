package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ValuationSnapshot(
        UUID id,
        SnapshotScope scopeType,
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

    public ValuationSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(scopeType);
        Objects.requireNonNull(totalCostBasis);
        Objects.requireNonNull(totalMarketValue);
        Objects.requireNonNull(unrealizedPnl);
        Objects.requireNonNull(capturedAt);
        if ((scopeType == SnapshotScope.ALL && accountId != null)
                || (scopeType == SnapshotScope.ACCOUNT
                        && accountId == null)) {
            throw new IllegalArgumentException(
                    "Snapshot scope and account do not match");
        }
    }

    public static ValuationSnapshot capture(
            UUID accountId,
            PnlTotals totals,
            Instant capturedAt) {
        return new ValuationSnapshot(
                UUID.randomUUID(),
                accountId == null
                        ? SnapshotScope.ALL
                        : SnapshotScope.ACCOUNT,
                accountId,
                totals.totalCostBasis(),
                totals.totalMarketValue(),
                totals.totalUnrealizedPnl(),
                totals.positionCount(),
                totals.pricedPositionCount(),
                totals.complete(),
                totals.mock(),
                totals.stale(),
                capturedAt);
    }
}
