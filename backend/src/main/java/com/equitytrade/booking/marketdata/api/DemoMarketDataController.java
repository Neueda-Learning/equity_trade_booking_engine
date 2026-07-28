package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.application.DemoMarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.DemoOutageView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/market-data/outage")
public class DemoMarketDataController {

    private final DemoMarketDataApplicationService demoService;

    public DemoMarketDataController(
            DemoMarketDataApplicationService demoService) {
        this.demoService = demoService;
    }

    @GetMapping
    public DemoOutageView status() {
        return demoService.status();
    }

    @PostMapping("/enable")
    public DemoOutageView enable() {
        return demoService.enable();
    }

    @PostMapping("/disable")
    public DemoOutageView disable() {
        return demoService.disable();
    }
}
