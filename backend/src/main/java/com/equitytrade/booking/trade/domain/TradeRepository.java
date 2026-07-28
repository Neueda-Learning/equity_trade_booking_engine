package com.equitytrade.booking.trade.domain;

import java.util.UUID;

public interface TradeRepository {

    Trade save(Trade trade);

    TradePage findAll(UUID accountId, int page, int size);
}
