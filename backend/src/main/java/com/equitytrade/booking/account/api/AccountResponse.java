package com.equitytrade.booking.account.api;

import com.equitytrade.booking.account.application.AccountView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "AccountResponse")
public record AccountResponse(
        @Schema(example = "bb06cce4-21c1-45ce-9bb4-0ebc6b96326c")
        UUID id,
        @Schema(example = "Primary Brokerage")
        String name,
        @Schema(example = "Example Broker")
        String broker,
        @Schema(nullable = true, example = "4242")
        String accountNumberLast4,
        @Schema(allowableValues = "USD", example = "USD")
        String baseCurrency,
        @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
        String status,
        Instant createdAt,
        Instant updatedAt) {

    static AccountResponse from(AccountView account) {
        return new AccountResponse(
                account.id(),
                account.name(),
                account.broker(),
                account.accountNumberLast4(),
                account.baseCurrency(),
                account.status(),
                account.createdAt(),
                account.updatedAt());
    }
}
