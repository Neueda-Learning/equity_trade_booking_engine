package com.equitytrade.booking.account.application;

public record AccountCommand(
        String name,
        String broker,
        String accountNumberLast4) {
}
