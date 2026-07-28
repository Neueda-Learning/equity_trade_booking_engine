package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/market-data/quotes")
public class MarketDataController {

    private final MarketDataApplicationService marketDataApplicationService;

    public MarketDataController(
            MarketDataApplicationService marketDataApplicationService) {
        this.marketDataApplicationService = marketDataApplicationService;
    }

    @GetMapping("/{ticker}")
    public MarketQuoteResponse quote(@PathVariable String ticker) {
        return MarketQuoteResponse.from(
                marketDataApplicationService.quote(ticker));
    }

    @PostMapping("/{ticker}/refresh")
    public MarketQuoteResponse refresh(@PathVariable String ticker) {
        return MarketQuoteResponse.from(
                marketDataApplicationService.refresh(ticker));
    }

    @GetMapping
    public MarketQuoteListResponse quotes(
            @RequestParam(required = false) UUID accountId) {
        return new MarketQuoteListResponse(
                marketDataApplicationService.quotes(accountId).stream()
                        .map(MarketQuoteResponse::from)
                        .toList());
    }
}
