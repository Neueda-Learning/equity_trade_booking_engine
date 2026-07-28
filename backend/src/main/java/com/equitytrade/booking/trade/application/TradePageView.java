package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.TradePage;

import java.util.List;

public record TradePageView(
        List<TradeView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static TradePageView from(TradePage tradePage) {
        return new TradePageView(
                tradePage.items().stream().map(TradeView::from).toList(),
                tradePage.page(),
                tradePage.size(),
                tradePage.totalElements(),
                tradePage.totalPages());
    }
}
