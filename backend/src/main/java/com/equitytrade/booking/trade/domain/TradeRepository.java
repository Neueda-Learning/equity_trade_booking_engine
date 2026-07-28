package com.equitytrade.booking.trade.domain;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TradeRepository {

    Trade save(Trade trade);

    Optional<Trade> findById(UUID id);

    List<Trade> findBookedByAccountAndTicker(UUID accountId, String ticker);

    List<Trade> findAllBooked();

    TradePage findAll(UUID accountId, int page, int size);
}
