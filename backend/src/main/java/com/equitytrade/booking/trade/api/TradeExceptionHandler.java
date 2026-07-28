package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.TradeUseCaseValidationException;
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

@RestControllerAdvice
public class TradeExceptionHandler {

    private static final URI VALIDATION_PROBLEM_TYPE =
            URI.create("urn:equity-trade:problem:validation");
    private static final String VALIDATION_PROBLEM_TITLE =
            "Request validation failed";
    private static final String VALIDATION_PROBLEM_DETAIL =
            "One or more fields are invalid.";

    @ExceptionHandler(TradeUseCaseValidationException.class)
    ResponseEntity<ProblemDetail> handleTradeValidation(
            TradeUseCaseValidationException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.errors().forEach(
                error -> errors.putIfAbsent(error.field(), error.message()));
        return badRequest(errors, request);
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
                default -> "has an invalid value";
            };
        }
        return badRequest(Map.of(field, message), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return badRequest(
                Map.of(exception.getName(), "must be an integer"),
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                VALIDATION_PROBLEM_DETAIL);
        problem.setType(VALIDATION_PROBLEM_TYPE);
        problem.setTitle(VALIDATION_PROBLEM_TITLE);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", Map.copyOf(errors));
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
