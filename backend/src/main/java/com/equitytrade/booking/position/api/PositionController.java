package com.equitytrade.booking.position.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.position.application.PositionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Positions")
public class PositionController {

    private final PositionApplicationService positionApplicationService;

    public PositionController(
            PositionApplicationService positionApplicationService) {
        this.positionApplicationService = positionApplicationService;
    }

    @GetMapping("/api/positions")
    @Operation(
            summary = "List positions",
            description = """
                    Replays BOOKED trades using weighted average cost.
                    Without accountId, identical tickers are aggregated across
                    all accounts. Zero positions are omitted.
                    """)
    @ApiResponse(
            responseCode = "400",
            description = "Invalid accountId",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public List<PositionResponse> list(
            @Parameter(description = "Optional account filter")
            @RequestParam(required = false) UUID accountId) {
        return responses(positionApplicationService.list(accountId));
    }

    @GetMapping("/api/accounts/{accountId}/positions")
    @Operation(summary = "List positions for one account")
    @ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Invalid account UUID",
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
    public List<PositionResponse> listForAccount(
            @PathVariable UUID accountId) {
        return responses(positionApplicationService.list(accountId));
    }

    private List<PositionResponse> responses(
            List<com.equitytrade.booking.position.application.PositionView>
                    positions) {
        return positions.stream().map(PositionResponse::from).toList();
    }
}
