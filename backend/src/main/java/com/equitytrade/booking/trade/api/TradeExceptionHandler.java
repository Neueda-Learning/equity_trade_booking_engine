package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.TradeUseCaseValidationException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class TradeExceptionHandler {

    private final Clock clock;

    public TradeExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(TradeUseCaseValidationException.class)
    ResponseEntity<ApiErrorResponse> handleTradeValidation(
            TradeUseCaseValidationException exception) {
        List<FieldErrorResponse> fieldErrors = exception.errors().stream()
                .map(error -> new FieldErrorResponse(
                        error.field(), error.message()))
                .toList();
        return badRequest("Trade validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException exception) {
        String field = "request";
        String message = "must contain valid JSON values";
        if (exception.getCause() instanceof InvalidFormatException invalid
                && !invalid.getPath().isEmpty()) {
            field = invalid.getPath().getLast().getFieldName();
            message = switch (field) {
                case "executedAt" -> "must be a valid ISO-8601 timestamp";
                case "quantity", "tradePrice" -> "must be a valid decimal number";
                default -> "has an invalid value";
            };
        }
        return badRequest(
                "Request body could not be read",
                List.of(new FieldErrorResponse(field, message)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return badRequest(
                "Request parameter is invalid",
                List.of(new FieldErrorResponse(
                        exception.getName(), "must be an integer")));
    }

    private ResponseEntity<ApiErrorResponse> badRequest(
            String message,
            List<FieldErrorResponse> fieldErrors) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                Instant.now(clock),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                fieldErrors));
    }
}
