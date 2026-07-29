package com.equitytrade.booking.pnl.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.pnl.application.DashboardApplicationService;
import com.equitytrade.booking.pnl.application.DashboardView;
import com.equitytrade.booking.pnl.application.ValuationHistoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardApplicationService dashboardService;

    public DashboardController(
            DashboardApplicationService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(
            summary = "Get the current dashboard",
            description = """
                    Returns unrealized P&L totals, position details, account
                    counts, recent activity, quote-state summary, and capturedAt.
                    """)
    public DashboardView get(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId) {
        return dashboardService.get(accountId);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh dashboard quotes and capture valuation",
            description = """
                    Force-refreshes each current position independently, retains
                    partial successes, calculates unrealized P&L, and writes an
                    ALL or ACCOUNT valuation snapshot to MySQL.
                    """)
    public DashboardView refresh(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId) {
        return dashboardService.refresh(accountId);
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get daily valuation history",
            description = """
                    Replays BOOKED trades by executedAt and values each UTC day
                    with historical closing prices. Weekends and market
                    holidays carry forward the most recent close.
                    """)
    @ApiResponse(
            responseCode = "400",
            description = "Invalid accountId or range",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public ValuationHistoryView history(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId,
            @Parameter(
                    description = "UTC history range",
                    schema = @Schema(
                            allowableValues = {"1D", "7D", "30D", "ALL"},
                            defaultValue = "30D"))
            @RequestParam(defaultValue = "30D") String range) {
        return dashboardService.history(accountId, range);
    }
}
