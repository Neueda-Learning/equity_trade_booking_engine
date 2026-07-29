package com.equitytrade.booking.pnl.domain;

import java.util.List;
import java.util.UUID;

public interface HistoricalTradeSource {

    List<HistoricalTrade> findBooked(UUID accountId);
}
