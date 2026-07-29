package com.equitytrade.booking.marketdata.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_quote_snapshots")
class MarketQuoteSnapshotJpaEntity {

    @Id
    @Column(length = 36, nullable = false, columnDefinition = "char(36)")
    private String id;

    @Column(length = 32, nullable = false)
    private String ticker;

    @Column(precision = 25, scale = 6, nullable = false)
    private BigDecimal price;

    @Column(
            name = "previous_close",
            precision = 25,
            scale = 6,
            nullable = false)
    private BigDecimal previousClose;

    @Column(
            name = "market_timestamp",
            nullable = false,
            columnDefinition = "datetime(6)")
    private LocalDateTime marketTimestamp;

    @Column(
            name = "fetched_at",
            nullable = false,
            columnDefinition = "datetime(6)")
    private LocalDateTime fetchedAt;

    @Column(length = 32, nullable = false)
    private String source;

    @Column(nullable = false)
    private boolean mock;

    @Column(
            name = "persisted_at",
            nullable = false,
            columnDefinition = "datetime(6)")
    private LocalDateTime persistedAt;

    protected MarketQuoteSnapshotJpaEntity() {
    }

    MarketQuoteSnapshotJpaEntity(
            String id,
            String ticker,
            BigDecimal price,
            BigDecimal previousClose,
            LocalDateTime marketTimestamp,
            LocalDateTime fetchedAt,
            String source,
            boolean mock,
            LocalDateTime persistedAt) {
        this.id = id;
        this.ticker = ticker;
        this.price = price;
        this.previousClose = previousClose;
        this.marketTimestamp = marketTimestamp;
        this.fetchedAt = fetchedAt;
        this.source = source;
        this.mock = mock;
        this.persistedAt = persistedAt;
    }

    String getId() {
        return id;
    }

    String getTicker() {
        return ticker;
    }

    BigDecimal getPrice() {
        return price;
    }

    BigDecimal getPreviousClose() {
        return previousClose;
    }

    LocalDateTime getMarketTimestamp() {
        return marketTimestamp;
    }

    LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    String getSource() {
        return source;
    }

    boolean isMock() {
        return mock;
    }

    LocalDateTime getPersistedAt() {
        return persistedAt;
    }
}
