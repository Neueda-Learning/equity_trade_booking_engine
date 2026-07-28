package com.equitytrade.booking.pnl.api;

import com.equitytrade.booking.pnl.application.DashboardApplicationService;
import com.equitytrade.booking.pnl.application.DashboardView;
import com.equitytrade.booking.pnl.application.ValuationHistoryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardApplicationService dashboardService;

    public DashboardController(
            DashboardApplicationService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardView get(
            @RequestParam(required = false) UUID accountId) {
        return dashboardService.get(accountId);
    }

    @PostMapping("/refresh")
    public DashboardView refresh(
            @RequestParam(required = false) UUID accountId) {
        return dashboardService.refresh(accountId);
    }

    @GetMapping("/history")
    public ValuationHistoryView history(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "30D") String range) {
        return dashboardService.history(accountId, range);
    }
}
