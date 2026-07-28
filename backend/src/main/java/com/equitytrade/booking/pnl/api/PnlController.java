package com.equitytrade.booking.pnl.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.pnl.application.PnlApplicationService;
import com.equitytrade.booking.pnl.application.PnlView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pnl")
@Tag(name = "P&L")
public class PnlController {

    private final PnlApplicationService pnlService;

    public PnlController(PnlApplicationService pnlService) {
        this.pnlService = pnlService;
    }

    @GetMapping
    @Operation(
            summary = "Get unrealized P&L",
            description = """
                    Calculates market value and unrealized P&L in the backend
                    using BOOKED positions, weighted average cost, and available
                    quotes. Missing quotes remain null and make totals incomplete.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current unrealized P&L"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid accountId",
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
                                        ProblemDetailsDocumentation.class)))
    })
    public PnlView get(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId) {
        return pnlService.get(accountId);
    }
}
