package com.equitytrade.booking.pnl.infrastructure.persistence;

import com.equitytrade.booking.pnl.domain.SnapshotScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "valuation_snapshots")
class ValuationSnapshotJpaEntity {

    @Id
    @Column(length = 36, nullable = false, columnDefinition = "char(36)")
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 16, nullable = false)
    private SnapshotScope scopeType;

    @Column(
            name = "account_id",
            length = 36,
            columnDefinition = "char(36)")
    private String accountId;

    @Column(name = "total_cost_basis", precision = 25, scale = 6, nullable = false)
    private BigDecimal totalCostBasis;

    @Column(name = "total_market_value", precision = 25, scale = 6, nullable = false)
    private BigDecimal totalMarketValue;

    @Column(name = "unrealized_pnl", precision = 25, scale = 6, nullable = false)
    private BigDecimal unrealizedPnl;

    @Column(name = "position_count", nullable = false)
    private int positionCount;

    @Column(name = "priced_position_count", nullable = false)
    private int pricedPositionCount;

    @Column(nullable = false)
    private boolean complete;

    @Column(nullable = false)
    private boolean mock;

    @Column(nullable = false)
    private boolean stale;

    @Column(
            name = "captured_at",
            nullable = false,
            columnDefinition = "datetime(6)")
    private LocalDateTime capturedAt;

    protected ValuationSnapshotJpaEntity() {
    }

    ValuationSnapshotJpaEntity(
            String id,
            SnapshotScope scopeType,
            String accountId,
            BigDecimal totalCostBasis,
            BigDecimal totalMarketValue,
            BigDecimal unrealizedPnl,
            int positionCount,
            int pricedPositionCount,
            boolean complete,
            boolean mock,
            boolean stale,
            LocalDateTime capturedAt) {
        this.id = id;
        this.scopeType = scopeType;
        this.accountId = accountId;
        this.totalCostBasis = totalCostBasis;
        this.totalMarketValue = totalMarketValue;
        this.unrealizedPnl = unrealizedPnl;
        this.positionCount = positionCount;
        this.pricedPositionCount = pricedPositionCount;
        this.complete = complete;
        this.mock = mock;
        this.stale = stale;
        this.capturedAt = capturedAt;
    }

    String getId() {
        return id;
    }

    SnapshotScope getScopeType() {
        return scopeType;
    }

    String getAccountId() {
        return accountId;
    }

    BigDecimal getTotalCostBasis() {
        return totalCostBasis;
    }

    BigDecimal getTotalMarketValue() {
        return totalMarketValue;
    }

    BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    int getPositionCount() {
        return positionCount;
    }

    int getPricedPositionCount() {
        return pricedPositionCount;
    }

    boolean isComplete() {
        return complete;
    }

    boolean isMock() {
        return mock;
    }

    boolean isStale() {
        return stale;
    }

    LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}
