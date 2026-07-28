package com.equitytrade.booking.trade.domain;

public interface TradeRepository {

    Trade save(Trade trade);

    TradePage findAll(int page, int size);
}
