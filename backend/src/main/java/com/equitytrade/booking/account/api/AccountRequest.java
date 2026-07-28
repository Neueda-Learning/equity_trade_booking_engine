package com.equitytrade.booking.account.api;

public record AccountRequest(
        String name,
        String broker,
        String accountNumberLast4) {
}
