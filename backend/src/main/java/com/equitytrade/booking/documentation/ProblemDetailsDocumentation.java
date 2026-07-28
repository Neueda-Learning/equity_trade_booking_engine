package com.equitytrade.booking.documentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.Map;

@Schema(
        name = "ProblemDetails",
        description = "RFC 9457-style error response with field-level errors")
public record ProblemDetailsDocumentation(
        @Schema(
                example = "urn:equity-trade:problem:validation",
                description = "Stable URI identifying the problem category")
        URI type,
        @Schema(example = "Request validation failed")
        String title,
        @Schema(example = "400")
        int status,
        @Schema(example = "One or more fields are invalid.")
        String detail,
        @Schema(example = "/api/trades")
        URI instance,
        @Schema(
                description = "Field or parameter name mapped to a safe message",
                example = "{\"quantity\":\"must be greater than 0\"}")
        Map<String, String> errors) {
}
