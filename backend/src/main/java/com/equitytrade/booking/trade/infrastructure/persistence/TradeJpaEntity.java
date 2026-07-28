package com.equitytrade.booking.trade.infrastructure.persistence;

import com.equitytrade.booking.trade.domain.TradeSide;
import com.equitytrade.booking.trade.domain.TradeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
class TradeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(name = "account_id", nullable = false, length = 36,
            columnDefinition = "char(36)")
    private String accountId;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 8)
    private TradeSide side;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "trade_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal tradePrice;

    @Column(name = "executed_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TradeStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    protected TradeJpaEntity() {
    }

    TradeJpaEntity(
            String id,
            String accountId,
            String ticker,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            LocalDateTime executedAt,
            TradeStatus status,
            LocalDateTime createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
        this.tradePrice = tradePrice;
        this.executedAt = executedAt;
        this.status = status;
        this.createdAt = createdAt;
    }

    String getId() {
        return id;
    }

    String getAccountId() {
        return accountId;
    }

    String getTicker() {
        return ticker;
    }

    TradeSide getSide() {
        return side;
    }

    BigDecimal getQuantity() {
        return quantity;
    }

    BigDecimal getTradePrice() {
        return tradePrice;
    }

    LocalDateTime getExecutedAt() {
        return executedAt;
    }

    TradeStatus getStatus() {
        return status;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
