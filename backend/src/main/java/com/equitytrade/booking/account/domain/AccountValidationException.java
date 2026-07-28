package com.equitytrade.booking.account.domain;

import java.util.List;

public class AccountValidationException extends RuntimeException {

    private final List<AccountFieldViolation> violations;

    public AccountValidationException(List<AccountFieldViolation> violations) {
        super("Account validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<AccountFieldViolation> violations() {
        return violations;
    }
}
