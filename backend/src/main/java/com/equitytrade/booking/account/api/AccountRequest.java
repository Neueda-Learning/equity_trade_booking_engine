package com.equitytrade.booking.account.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "AccountRequest",
        description = "USD securities account fields",
        example = """
                {
                  "name": "Primary Brokerage",
                  "broker": "Example Broker",
                  "accountNumberLast4": "4242"
                }
                """)
public record AccountRequest(
        @Schema(example = "Primary Brokerage")
        String name,
        @Schema(example = "Example Broker")
        String broker,
        @Schema(
                nullable = true,
                pattern = "^\\d{4}$",
                example = "4242")
        String accountNumberLast4) {
}
