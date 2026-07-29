package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import com.equitytrade.booking.trade.application.CompleteTradeImportCommand;
import com.equitytrade.booking.trade.application.RegisterTradeImportCommand;
import com.equitytrade.booking.trade.application.TradeImportApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/trade-imports")
@Tag(name = "Activity CSV imports")
public class TradeImportController {

    private final TradeImportApplicationService service;

    public TradeImportController(TradeImportApplicationService service) {
        this.service = service;
    }

    @PostMapping("/registrations")
    @Operation(
            summary = "Register a CSV table before importing",
            description = """
                    Uses a SHA-256 content identity to detect a previously
                    imported table. A duplicate requires explicit confirmation;
                    confirming imports the complete table again.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Import registered"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid registration",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CSV table was imported previously",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public ResponseEntity<TradeImportResponse> register(
            @RequestBody RegisterTradeImportRequest request) {
        TradeImportResponse response = TradeImportResponse.from(
                service.register(new RegisterTradeImportCommand(
                        request.contentHash(),
                        request.fileName(),
                        request.rowCount(),
                        request.repeatConfirmed())));
        return ResponseEntity
                .created(URI.create(
                        "/api/trade-imports/" + response.importId()))
                .body(response);
    }

    @PatchMapping("/{importId}/result")
    @Operation(summary = "Record the result of the latest CSV import attempt")
    public TradeImportResponse complete(
            @PathVariable UUID importId,
            @RequestBody CompleteTradeImportRequest request) {
        return TradeImportResponse.from(service.complete(
                importId,
                new CompleteTradeImportCommand(
                        request.importCount(),
                        request.successCount(),
                        request.failureCount())));
    }
}
