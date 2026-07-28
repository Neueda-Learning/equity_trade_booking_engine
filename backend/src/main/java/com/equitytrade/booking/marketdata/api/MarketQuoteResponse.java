package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.MarketQuoteView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(
        name = "MarketQuoteResponse",
        description = """
                Quote with explicit source and cache state. A Finnhub quote is
                LIVE only when mock=false, cached=false, and stale=false.
                """)
public record MarketQuoteResponse(
        @Schema(example = "AAPL")
        String ticker,
        @Schema(example = "195.250000")
        BigDecimal price,
        @Schema(example = "193.800000")
        BigDecimal previousClose,
        @Schema(example = "1.450000")
        BigDecimal change,
        @Schema(example = "0.748194")
        BigDecimal changePercent,
        Instant marketTimestamp,
        Instant fetchedAt,
        @Schema(allowableValues = {"MOCK", "FINNHUB"})
        String source,
        @Schema(description = "True only for deterministic development quotes")
        boolean mock,
        @Schema(description = "Quote was read from Redis")
        boolean cached,
        @Schema(description = "Provider refresh failed and retained data is shown")
        boolean stale) {

    static MarketQuoteResponse from(MarketQuoteView quote) {
        return new MarketQuoteResponse(
                quote.ticker(),
                quote.price(),
                quote.previousClose(),
                quote.change(),
                quote.changePercent(),
                quote.marketTimestamp(),
                quote.fetchedAt(),
                quote.source(),
                quote.mock(),
                quote.cached(),
                quote.stale());
    }
}
