package com.equitytrade.booking.pnl.domain;

import java.util.List;
import java.util.UUID;

public interface DashboardContextSource {

    List<DashboardAccount> accounts();

    void requireAccount(UUID accountId);

    List<DashboardActivity> recentActivity(UUID accountId, int limit);
}
