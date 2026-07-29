package com.equitytrade.booking.trade.domain;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public record TradeImport(
        UUID id,
        String contentHash,
        String firstFileName,
        int rowCount,
        Instant firstImportedAt,
        Instant lastImportedAt,
        int importCount,
        TradeImportStatus status,
        int lastSuccessCount,
        int lastFailureCount) {

    public static TradeImport start(
            String contentHash,
            String fileName,
            int rowCount,
            Instant now) {
        return new TradeImport(
                idFromHash(contentHash),
                contentHash,
                fileName,
                rowCount,
                now,
                now,
                1,
                TradeImportStatus.IN_PROGRESS,
                0,
                0);
    }

    public TradeImport repeat(Instant now) {
        return new TradeImport(
                id,
                contentHash,
                firstFileName,
                rowCount,
                firstImportedAt,
                now,
                importCount + 1,
                TradeImportStatus.IN_PROGRESS,
                0,
                0);
    }

    public TradeImport complete(
            int expectedImportCount,
            int successCount,
            int failureCount,
            Instant now) {
        if (expectedImportCount != importCount) {
            throw new IllegalStateException("A newer import attempt is active.");
        }
        if (successCount < 0
                || failureCount < 0
                || successCount + failureCount != rowCount) {
            throw new IllegalArgumentException(
                    "Result counts must be non-negative and equal rowCount.");
        }
        TradeImportStatus completedStatus = failureCount == 0
                ? TradeImportStatus.COMPLETED
                : successCount == 0
                        ? TradeImportStatus.FAILED
                        : TradeImportStatus.PARTIAL;
        return new TradeImport(
                id,
                contentHash,
                firstFileName,
                rowCount,
                firstImportedAt,
                now,
                importCount,
                completedStatus,
                successCount,
                failureCount);
    }

    private static UUID idFromHash(String contentHash) {
        byte[] hash = HexFormat.of().parseHex(contentHash);
        ByteBuffer bytes = ByteBuffer.wrap(hash, 0, 16);
        long most = bytes.getLong();
        long least = bytes.getLong();
        most = (most & 0xffffffffffff0fffL) | 0x0000000000008000L;
        least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(most, least);
    }
}
