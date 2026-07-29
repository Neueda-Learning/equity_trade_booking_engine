package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.BookTradeCommand;
import com.equitytrade.booking.trade.application.TradeApplicationService;
import com.equitytrade.booking.trade.application.TradeView;
import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/trades")
@Tag(name = "Activity")
public class TradeController {

    private final TradeApplicationService tradeApplicationService;

    public TradeController(TradeApplicationService tradeApplicationService) {
        this.tradeApplicationService = tradeApplicationService;
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel a trade",
            description = """
                    Idempotently changes BOOKED to CANCELLED and preserves the
                    first cancellation time. Cancellation is rejected if the
                    remaining chronological ledger would become negative.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Complete trade state"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid trade UUID",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Trade does not exist",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Cancellation would create a negative position",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public TradeResponse cancel(@PathVariable UUID id) {
        return TradeResponse.from(tradeApplicationService.cancel(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a trade without destroying its audit record",
            description = """
                    Marks a BOOKED trade as CANCELLED with reason DELETED.
                    The row remains queryable and is excluded from positions.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit-preserved deletion"),
        @ApiResponse(
                responseCode = "404",
                description = "Trade does not exist",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Deletion conflicts with the ledger",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public TradeResponse delete(@PathVariable UUID id) {
        return TradeResponse.from(tradeApplicationService.delete(id));
    }

    @PostMapping("/{id}/amend")
    @Operation(
            summary = "Amend a booked trade",
            description = """
                    Cancels the original trade with reason AMENDED and creates
                    an audit-linked replacement in one transaction.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Amendment completed"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid amendment",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Trade or account does not exist",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Amendment conflicts with account or position",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public AmendTradeResponse amend(
            @PathVariable UUID id,
            @RequestBody CreateTradeRequest request) {
        return AmendTradeResponse.from(
                tradeApplicationService.amend(
                        id,
                        command(request)));
    }

    @PostMapping
    @Operation(
            summary = "Book a BUY or SELL",
            description = """
                    Books an immutable trade against an ACTIVE account. SELL is
                    rejected if chronological replay would become negative.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Trade booked"),
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Account does not exist",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Inactive account or insufficient position",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public ResponseEntity<TradeResponse> create(
            @RequestBody CreateTradeRequest request) {
        TradeView trade = tradeApplicationService.book(command(request));
        TradeResponse response = TradeResponse.from(trade);
        return ResponseEntity
                .created(URI.create("/api/trades/" + response.id()))
                .body(response);
    }

    @GetMapping
    @Operation(
            summary = "List trade activity",
            description = """
                    Returns a stable page sorted by executedAt DESC,
                    createdAt DESC, then id DESC. Client-controlled sorting is
                    not supported.
                    """)
    @ApiResponse(
            responseCode = "400",
            description = "Invalid accountId, page, or size",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public TradePageResponse list(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Zero-based page", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(
                    description = "Page size from 1 to 100",
                    example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return TradePageResponse.from(
                tradeApplicationService.list(accountId, page, size));
    }

    private BookTradeCommand command(CreateTradeRequest request) {
        return new BookTradeCommand(
                request.accountId(),
                request.ticker(),
                request.side(),
                request.quantity(),
                request.tradePrice(),
                request.executedAt());
    }
}
