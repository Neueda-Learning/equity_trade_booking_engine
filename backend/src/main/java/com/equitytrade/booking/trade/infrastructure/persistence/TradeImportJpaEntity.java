package com.equitytrade.booking.trade.infrastructure.persistence;

import com.equitytrade.booking.trade.domain.TradeImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_import_registry")
class TradeImportJpaEntity {

    @Id
    @Column(length = 36, nullable = false, columnDefinition = "char(36)")
    private String id;

    @Column(
            name = "content_hash",
            length = 64,
            nullable = false,
            unique = true,
            columnDefinition = "char(64)")
    private String contentHash;

    @Column(name = "first_file_name", length = 255, nullable = false)
    private String firstFileName;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(
            name = "first_imported_at",
            nullable = false,
            columnDefinition = "datetime(6)")
    private LocalDateTime firstImportedAt;

    @Column(
            name = "last_imported_at",
            nullable = false,
            columnDefinition = "datetime(6)")
    private LocalDateTime lastImportedAt;

    @Column(name = "import_count", nullable = false)
    private int importCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private TradeImportStatus status;

    @Column(name = "last_success_count", nullable = false)
    private int lastSuccessCount;

    @Column(name = "last_failure_count", nullable = false)
    private int lastFailureCount;

    protected TradeImportJpaEntity() {
    }

    TradeImportJpaEntity(
            String id,
            String contentHash,
            String firstFileName,
            int rowCount,
            LocalDateTime firstImportedAt,
            LocalDateTime lastImportedAt,
            int importCount,
            TradeImportStatus status,
            int lastSuccessCount,
            int lastFailureCount) {
        this.id = id;
        this.contentHash = contentHash;
        this.firstFileName = firstFileName;
        this.rowCount = rowCount;
        this.firstImportedAt = firstImportedAt;
        this.lastImportedAt = lastImportedAt;
        this.importCount = importCount;
        this.status = status;
        this.lastSuccessCount = lastSuccessCount;
        this.lastFailureCount = lastFailureCount;
    }

    String getId() {
        return id;
    }

    String getContentHash() {
        return contentHash;
    }

    String getFirstFileName() {
        return firstFileName;
    }

    int getRowCount() {
        return rowCount;
    }

    LocalDateTime getFirstImportedAt() {
        return firstImportedAt;
    }

    LocalDateTime getLastImportedAt() {
        return lastImportedAt;
    }

    int getImportCount() {
        return importCount;
    }

    TradeImportStatus getStatus() {
        return status;
    }

    int getLastSuccessCount() {
        return lastSuccessCount;
    }

    int getLastFailureCount() {
        return lastFailureCount;
    }
}
