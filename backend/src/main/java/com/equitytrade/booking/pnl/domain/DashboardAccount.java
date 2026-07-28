package com.equitytrade.booking.pnl.domain;

import java.util.UUID;

public record DashboardAccount(
        UUID id,
        String name,
        boolean active) {
}
