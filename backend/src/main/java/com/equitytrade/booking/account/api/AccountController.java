package com.equitytrade.booking.account.api;

import com.equitytrade.booking.account.application.AccountApplicationService;
import com.equitytrade.booking.account.application.AccountCommand;
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
public class AccountController {

    private final AccountApplicationService accountApplicationService;

    public AccountController(AccountApplicationService accountApplicationService) {
        this.accountApplicationService = accountApplicationService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @RequestBody AccountRequest request) {
        AccountResponse response = AccountResponse.from(
                accountApplicationService.create(toCommand(request)));
        return ResponseEntity
                .created(URI.create("/api/accounts/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountApplicationService.list().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountApplicationService.get(id));
    }

    @PatchMapping("/{id}")
    public AccountResponse update(
            @PathVariable UUID id,
            @RequestBody AccountRequest request) {
        return AccountResponse.from(
                accountApplicationService.update(id, toCommand(request)));
    }

    @PostMapping("/{id}/deactivate")
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
