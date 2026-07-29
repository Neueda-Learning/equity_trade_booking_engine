package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.InstrumentSearchView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "InstrumentSearchResponse")
public record InstrumentSearchResponse(List<Item> items) {

    static InstrumentSearchResponse from(
            List<InstrumentSearchView> results) {
        return new InstrumentSearchResponse(
                results.stream().map(Item::from).toList());
    }

    public record Item(
            String ticker,
            String name,
            String exchange,
            String type) {

        static Item from(InstrumentSearchView result) {
            return new Item(
                    result.ticker(),
                    result.name(),
                    result.exchange(),
                    result.type());
        }
    }
}
