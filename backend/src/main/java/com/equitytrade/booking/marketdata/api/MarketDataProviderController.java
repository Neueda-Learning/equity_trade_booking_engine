package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.MarketDataProviderStatusService;
import com.equitytrade.booking.marketdata.application.MarketDataProviderStatusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data/provider")
@Tag(name = "Market Data")
public class MarketDataProviderController {

    private final MarketDataProviderStatusService statusService;

    public MarketDataProviderController(
            MarketDataProviderStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get provider runtime status",
            description = """
                    Reports MOCK or FINNHUB configuration and safe success/failure
                    timestamps. Never returns an API key, request header, or raw
                    provider exception.
                    """)
    public MarketDataProviderStatusView status() {
        return statusService.get();
    }
}
