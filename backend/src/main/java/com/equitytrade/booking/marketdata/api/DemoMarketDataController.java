package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.marketdata.application.DemoMarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.DemoOutageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/market-data/outage")
@Tag(name = "Demo Only")
public class DemoMarketDataController {

    private final DemoMarketDataApplicationService demoService;

    public DemoMarketDataController(
            DemoMarketDataApplicationService demoService) {
        this.demoService = demoService;
    }

    @GetMapping
    @Operation(
            summary = "Read simulated outage state — Demo Only",
            description = "Available only when explicitly enabled with Finnhub.")
    @ApiResponse(
            responseCode = "404",
            description = "Demo controls are disabled",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public DemoOutageView status() {
        return demoService.status();
    }

    @PostMapping("/enable")
    @Operation(
            summary = "Enable simulated provider outage — Demo Only",
            description = "Fails external provider calls without deleting Redis.")
    @ApiResponse(
            responseCode = "404",
            description = "Demo controls are disabled",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public DemoOutageView enable() {
        return demoService.enable();
    }

    @PostMapping("/disable")
    @Operation(
            summary = "Disable simulated provider outage — Demo Only",
            description = "Restores calls to the configured Finnhub provider.")
    @ApiResponse(
            responseCode = "404",
            description = "Demo controls are disabled",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public DemoOutageView disable() {
        return demoService.disable();
    }
}
