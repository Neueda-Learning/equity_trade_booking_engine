package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PnlTotals;
import com.equitytrade.booking.pnl.domain.SnapshotScope;
import com.equitytrade.booking.pnl.domain.ValuationSnapshot;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

public record ValuationHistoryPointView(
        UUID id,
        String scopeType,
        UUID accountId,
        LocalDate valuationDate,
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal unrealizedPnl,
        int positionCount,
        int pricedPositionCount,
        boolean complete,
        boolean mock,
        boolean stale,
        Instant capturedAt) {

    static ValuationHistoryPointView from(ValuationSnapshot snapshot) {
        return new ValuationHistoryPointView(
                snapshot.id(),
                snapshot.scopeType().name(),
                snapshot.accountId(),
                LocalDate.ofInstant(snapshot.capturedAt(), ZoneOffset.UTC),
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

    static ValuationHistoryPointView from(
            UUID accountId,
            LocalDate valuationDate,
            PnlTotals totals) {
        String scope = accountId == null
                ? SnapshotScope.ALL.name()
                : SnapshotScope.ACCOUNT.name();
        UUID id = historicalId(accountId, valuationDate);
        return new ValuationHistoryPointView(
                id,
                scope,
                accountId,
                valuationDate,
                PnlDecimal.api(totals.totalCostBasis()),
                PnlDecimal.api(totals.totalMarketValue()),
                PnlDecimal.api(totals.totalUnrealizedPnl()),
                totals.positionCount(),
                totals.pricedPositionCount(),
                totals.complete(),
                totals.mock(),
                false,
                valuationDate.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    ValuationSnapshot toSnapshot() {
        return new ValuationSnapshot(
                id,
                SnapshotScope.valueOf(scopeType),
                accountId,
                totalCostBasis,
                totalMarketValue,
                unrealizedPnl,
                positionCount,
                pricedPositionCount,
                complete,
                mock,
                stale,
                capturedAt);
    }

    static UUID historicalId(
            UUID accountId,
            LocalDate valuationDate) {
        String scope = accountId == null
                ? SnapshotScope.ALL.name()
                : SnapshotScope.ACCOUNT.name();
        return UUID.nameUUIDFromBytes(
                (scope + ":" + accountId + ":" + valuationDate)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
