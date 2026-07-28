package com.equitytrade.booking.account.application;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super("Account not found");
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
