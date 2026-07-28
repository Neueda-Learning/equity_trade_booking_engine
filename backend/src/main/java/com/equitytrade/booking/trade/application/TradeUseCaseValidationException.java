package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.TradeValidationException;

import java.util.List;

public class TradeUseCaseValidationException extends RuntimeException {

    private final List<TradeValidationError> errors;

    public TradeUseCaseValidationException(List<TradeValidationError> errors) {
        super("Trade use case validation failed");
        this.errors = List.copyOf(errors);
    }

    static TradeUseCaseValidationException from(
            TradeValidationException exception) {
        return new TradeUseCaseValidationException(
                exception.violations().stream()
                        .map(violation -> new TradeValidationError(
                                violation.field(), violation.message()))
                        .toList());
    }

    static TradeUseCaseValidationException forField(
            String field,
            String message) {
        return new TradeUseCaseValidationException(
                List.of(new TradeValidationError(field, message)));
    }

    public List<TradeValidationError> errors() {
        return errors;
    }
}
