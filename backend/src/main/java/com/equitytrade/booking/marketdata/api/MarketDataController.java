package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/market-data/quotes")
@Tag(name = "Market Data")
public class MarketDataController {

    private final MarketDataApplicationService marketDataApplicationService;

    public MarketDataController(
            MarketDataApplicationService marketDataApplicationService) {
        this.marketDataApplicationService = marketDataApplicationService;
    }

    @GetMapping("/{ticker}")
    @Operation(
            summary = "Get a market quote",
            description = """
                    Returns a fresh cache hit or fetches the configured provider.
                    cached and stale are independent flags; stale data is never
                    represented as LIVE.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Available quote"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid ticker",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Provider has no usable quote",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Provider failed and no cached quote exists",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public MarketQuoteResponse quote(@PathVariable String ticker) {
        return MarketQuoteResponse.from(
                marketDataApplicationService.quote(ticker));
    }

    @PostMapping("/{ticker}/refresh")
    @Operation(
            summary = "Force-refresh a market quote",
            description = """
                    Calls the configured provider. On failure, a retained Redis
                    quote is returned as CACHED and STALE; Mock is never used as
                    an implicit Finnhub fallback.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fresh or stale fallback quote"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid ticker",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Provider has no usable quote",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Provider failed and no cached quote exists",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public MarketQuoteResponse refresh(@PathVariable String ticker) {
        return MarketQuoteResponse.from(
                marketDataApplicationService.refresh(ticker));
    }

    @GetMapping
    @Operation(
            summary = "List quotes for current positions",
            description = "Returns ticker-ascending quotes for non-zero BOOKED positions.")
    @ApiResponse(
            responseCode = "404",
            description = "Account does not exist",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public MarketQuoteListResponse quotes(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId) {
        return new MarketQuoteListResponse(
                marketDataApplicationService.quotes(accountId).stream()
                        .map(MarketQuoteResponse::from)
                        .toList());
    }
}
