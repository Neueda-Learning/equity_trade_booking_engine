package com.equitytrade.booking.trade.application;

public record AmendTradeResult(
        TradeView cancelledTrade,
        TradeView replacementTrade) {
}
