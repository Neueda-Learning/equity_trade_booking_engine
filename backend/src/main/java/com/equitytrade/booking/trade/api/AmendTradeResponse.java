package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.AmendTradeResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AmendTradeResponse")
public record AmendTradeResponse(
        TradeResponse cancelledTrade,
        TradeResponse replacementTrade) {

    static AmendTradeResponse from(AmendTradeResult result) {
        return new AmendTradeResponse(
                TradeResponse.from(result.cancelledTrade()),
                TradeResponse.from(result.replacementTrade()));
    }
}
