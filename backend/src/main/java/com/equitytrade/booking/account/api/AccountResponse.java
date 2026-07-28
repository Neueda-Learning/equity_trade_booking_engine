package com.equitytrade.booking.account.api;

import com.equitytrade.booking.account.application.AccountView;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String broker,
        String accountNumberLast4,
        String baseCurrency,
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
