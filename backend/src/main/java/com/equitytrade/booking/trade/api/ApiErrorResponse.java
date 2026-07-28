package com.equitytrade.booking.trade.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<FieldErrorResponse> fieldErrors) {
}
