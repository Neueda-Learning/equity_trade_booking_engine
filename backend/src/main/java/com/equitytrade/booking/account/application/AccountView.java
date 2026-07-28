package com.equitytrade.booking.account.application;

import com.equitytrade.booking.account.domain.Account;

import java.time.Instant;
import java.util.UUID;

public record AccountView(
        UUID id,
        String name,
        String broker,
        String accountNumberLast4,
        String baseCurrency,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountView from(Account account) {
        return new AccountView(
                account.id(),
                account.name(),
                account.broker(),
                account.accountNumberLast4(),
                account.baseCurrency(),
                account.status().name(),
                account.createdAt(),
                account.updatedAt());
    }
}
