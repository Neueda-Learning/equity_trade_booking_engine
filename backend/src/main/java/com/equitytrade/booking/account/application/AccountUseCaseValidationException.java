package com.equitytrade.booking.account.application;

import com.equitytrade.booking.account.domain.AccountValidationException;

import java.util.List;

public class AccountUseCaseValidationException extends RuntimeException {

    private final List<AccountValidationError> errors;

    private AccountUseCaseValidationException(List<AccountValidationError> errors) {
        super("Account use case validation failed");
        this.errors = List.copyOf(errors);
    }

    public static AccountUseCaseValidationException from(
            AccountValidationException exception) {
        return new AccountUseCaseValidationException(
                exception.violations().stream()
                        .map(violation -> new AccountValidationError(
                                violation.field(), violation.message()))
                        .toList());
    }

    public List<AccountValidationError> errors() {
        return errors;
    }
}
