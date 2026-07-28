package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.account.application.AccountConflictException;
import com.equitytrade.booking.account.application.AccountNotFoundException;
import com.equitytrade.booking.account.application.AccountUseCaseValidationException;
import com.equitytrade.booking.marketdata.application.MarketDataUnavailableException;
import com.equitytrade.booking.marketdata.application.MarketDataValidationException;
import com.equitytrade.booking.trade.application.TradeUseCaseValidationException;
import com.equitytrade.booking.trade.application.TradeConflictException;
import com.equitytrade.booking.trade.application.TradeNotFoundException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class TradeExceptionHandler {

    private static final URI VALIDATION_PROBLEM_TYPE =
            URI.create("urn:equity-trade:problem:validation");
    private static final String VALIDATION_PROBLEM_TITLE =
            "Request validation failed";
    private static final String VALIDATION_PROBLEM_DETAIL =
            "One or more fields are invalid.";
    private static final URI NOT_FOUND_PROBLEM_TYPE =
            URI.create("urn:equity-trade:problem:not-found");
    private static final URI CONFLICT_PROBLEM_TYPE =
            URI.create("urn:equity-trade:problem:conflict");
    private static final URI MARKET_DATA_UNAVAILABLE_PROBLEM_TYPE =
            URI.create("urn:equity-trade:problem:market-data-unavailable");

    @ExceptionHandler(TradeUseCaseValidationException.class)
    ResponseEntity<ProblemDetail> handleTradeValidation(
            TradeUseCaseValidationException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.errors().forEach(
                error -> errors.putIfAbsent(error.field(), error.message()));
        return badRequest(errors, request);
    }

    @ExceptionHandler(AccountUseCaseValidationException.class)
    ResponseEntity<ProblemDetail> handleAccountValidation(
            AccountUseCaseValidationException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.errors().forEach(
                error -> errors.putIfAbsent(error.field(), error.message()));
        return badRequest(errors, request);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> handleAccountNotFound(
            AccountNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                NOT_FOUND_PROBLEM_TYPE,
                "Account not found",
                "The requested account does not exist.",
                Map.of("accountId", "does not exist"),
                request);
    }

    @ExceptionHandler(AccountConflictException.class)
    ResponseEntity<ProblemDetail> handleAccountConflict(
            AccountConflictException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                CONFLICT_PROBLEM_TYPE,
                "Request conflict",
                "The request conflicts with the current account state.",
                Map.of(exception.field(), exception.reason()),
                request);
    }

    @ExceptionHandler(TradeConflictException.class)
    ResponseEntity<ProblemDetail> handleTradeConflict(
            TradeConflictException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                CONFLICT_PROBLEM_TYPE,
                "Request conflict",
                "The request conflicts with the current position.",
                Map.of(exception.field(), exception.reason()),
                request);
    }

    @ExceptionHandler(TradeNotFoundException.class)
    ResponseEntity<ProblemDetail> handleTradeNotFound(
            TradeNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                NOT_FOUND_PROBLEM_TYPE,
                "Trade not found",
                "The requested trade does not exist.",
                Map.of("id", "does not exist"),
                request);
    }

    @ExceptionHandler(MarketDataValidationException.class)
    ResponseEntity<ProblemDetail> handleMarketDataValidation(
            MarketDataValidationException exception,
            HttpServletRequest request) {
        return badRequest(
                Map.of(exception.field(), exception.reason()),
                request);
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    ResponseEntity<ProblemDetail> handleMarketDataUnavailable(
            MarketDataUnavailableException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                MARKET_DATA_UNAVAILABLE_PROBLEM_TYPE,
                "Market data unavailable",
                "No market quote is currently available.",
                Map.of(
                        "ticker",
                        "market data is unavailable for "
                                + exception.ticker()),
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        String field = "request";
        String message = "must contain valid JSON values";
        if (exception.getCause() instanceof InvalidFormatException invalid
                && !invalid.getPath().isEmpty()) {
            field = invalid.getPath().getLast().getFieldName();
            message = switch (field) {
                case "executedAt" -> "must be a valid ISO-8601 timestamp";
                case "quantity", "tradePrice" -> "must be a valid decimal number";
                case "accountId" -> "must be a valid UUID";
                default -> "has an invalid value";
            };
        }
        return badRequest(Map.of(field, message), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        String message = exception.getRequiredType() == UUID.class
                ? "must be a valid UUID"
                : "must be an integer";
        return badRequest(
                Map.of(exception.getName(), message),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBeanValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "has an invalid value"
                                : error.getDefaultMessage()));
        return badRequest(errors, request);
    }

    private ResponseEntity<ProblemDetail> badRequest(
            Map<String, String> errors,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                VALIDATION_PROBLEM_TYPE,
                VALIDATION_PROBLEM_TITLE,
                VALIDATION_PROBLEM_DETAIL,
                errors,
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            Map<String, String> errors,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", Map.copyOf(errors));
        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
