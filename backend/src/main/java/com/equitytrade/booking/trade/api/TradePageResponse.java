package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.TradePageView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TradePageResponse", description = "Stable server-sorted trade page")
public record TradePageResponse(
        List<TradeResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static TradePageResponse from(TradePageView tradePage) {
        return new TradePageResponse(
                tradePage.items().stream().map(TradeResponse::from).toList(),
                tradePage.page(),
                tradePage.size(),
                tradePage.totalElements(),
                tradePage.totalPages());
    }
}
