package com.equitytrade.booking.documentation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Equity Trade Booking Engine API",
                version = "1.0",
                description = """
                        Single-user, multi-account USD equity booking API.
                        Positions and unrealized P&L include BOOKED trades only.
                        Market quotes explicitly identify MOCK, LIVE, CACHED,
                        and STALE states. The API does not support short selling,
                        cash balances, multi-currency, or realized P&L.
                        """,
                contact = @Contact(name = "Equity Trade Booking Team"),
                license = @License(name = "Internal learning project")),
        tags = {
            @Tag(name = "Accounts", description = "Securities account lifecycle"),
            @Tag(name = "Activity", description = "BUY/SELL booking, cancellation, and ledger pagination"),
            @Tag(name = "Positions", description = "BOOKED position views using weighted average cost"),
            @Tag(name = "Market Data", description = "MOCK or Finnhub quotes with Redis cache state"),
            @Tag(name = "P&L", description = "Backend-calculated unrealized profit and loss"),
            @Tag(name = "Dashboard", description = "Portfolio valuation, activity, and snapshot history"),
            @Tag(name = "Demo Only", description = "Explicitly enabled, in-memory demonstration controls")
        })
public class OpenApiConfiguration {
}
