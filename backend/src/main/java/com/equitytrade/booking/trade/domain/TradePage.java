package com.equitytrade.booking.trade.domain;

import java.util.List;

public record TradePage(
        List<Trade> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public TradePage {
        items = List.copyOf(items);
    }
}
