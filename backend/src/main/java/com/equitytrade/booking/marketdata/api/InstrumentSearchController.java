package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.marketdata.application.InstrumentSearchApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data/instruments")
@Tag(name = "Market Data")
public class InstrumentSearchController {

    private final InstrumentSearchApplicationService service;

    public InstrumentSearchController(
            InstrumentSearchApplicationService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search supported US securities",
            description = """
                    Searches the configured provider by ticker or company name.
                    Results are limited to supported US stocks, ADRs, and ETFs.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matching instruments"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid query",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Instrument provider unavailable",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public InstrumentSearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int limit) {
        return InstrumentSearchResponse.from(
                service.search(query, limit));
    }
}
