package com.equitytrade.booking.pnl.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardActivity(
        UUID id,
        UUID accountId,
        String accountName,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        String status,
        Instant executedAt,
        Instant cancelledAt,
        String cancellationReason) {
}
