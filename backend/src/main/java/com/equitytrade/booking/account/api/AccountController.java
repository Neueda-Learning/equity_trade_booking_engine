package com.equitytrade.booking.account.api;

import com.equitytrade.booking.account.application.AccountApplicationService;
import com.equitytrade.booking.account.application.AccountCommand;
import com.equitytrade.booking.documentation.ProblemDetailsDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountApplicationService accountApplicationService;

    public AccountController(AccountApplicationService accountApplicationService) {
        this.accountApplicationService = accountApplicationService;
    }

    @PostMapping
    @Operation(
            summary = "Create a securities account",
            description = "Creates an ACTIVE USD account. Account names are unique.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid name, broker, or last four digits",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Account name already exists",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public ResponseEntity<AccountResponse> create(
            @RequestBody AccountRequest request) {
        AccountResponse response = AccountResponse.from(
                accountApplicationService.create(toCommand(request)));
        return ResponseEntity
                .created(URI.create("/api/accounts/" + response.id()))
                .body(response);
    }

    @GetMapping
    @Operation(
            summary = "List securities accounts",
            description = "Returns ACTIVE and INACTIVE accounts.")
    public List<AccountResponse> list() {
        return accountApplicationService.list().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a securities account")
    @ApiResponse(
            responseCode = "404",
            description = "Account does not exist",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountApplicationService.get(id));
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Update a securities account",
            description = "Updates name, broker, and optional last four digits.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Invalid account fields",
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
                description = "Account name already exists",
                content = @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(
                                implementation =
                                        ProblemDetailsDocumentation.class)))
    })
    public AccountResponse update(
            @PathVariable UUID id,
            @RequestBody AccountRequest request) {
        return AccountResponse.from(
                accountApplicationService.update(id, toCommand(request)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate a securities account",
            description = "Idempotently marks an account INACTIVE; it is never deleted.")
    @ApiResponse(
            responseCode = "404",
            description = "Account does not exist",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(
                            implementation =
                                    ProblemDetailsDocumentation.class)))
    public AccountResponse deactivate(@PathVariable UUID id) {
        return AccountResponse.from(accountApplicationService.deactivate(id));
    }

    private AccountCommand toCommand(AccountRequest request) {
        return new AccountCommand(
                request.name(),
                request.broker(),
                request.accountNumberLast4());
    }
}
