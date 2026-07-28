package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.MarketDataProviderStatusService;
import com.equitytrade.booking.marketdata.application.MarketDataProviderStatusView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data/provider")
public class MarketDataProviderController {

    private final MarketDataProviderStatusService statusService;

    public MarketDataProviderController(
            MarketDataProviderStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public MarketDataProviderStatusView status() {
        return statusService.get();
    }
}
