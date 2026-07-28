package com.equitytrade.booking.marketdata.domain;

import java.util.List;
import java.util.UUID;

public interface PositionTickerSource {

    List<String> findTickers(UUID accountId);
}
