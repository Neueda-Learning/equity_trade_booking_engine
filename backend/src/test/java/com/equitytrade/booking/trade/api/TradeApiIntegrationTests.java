package com.equitytrade.booking.trade.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TradeApiIntegrationTests.FixedClockConfiguration.class)
@SpringBootTest
@Transactional
class TradeApiIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-07-28T06:30:30Z");
    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void booksNormalizedBuyTradeAndListsIt() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "00000000-0000-0000-0000-000000000001",
                                  "ticker": " aapl ",
                                  "side": "BUY",
                                  "quantity": 10.5,
                                  "tradePrice": 195.25,
                                  "executedAt": "2026-07-28T06:30:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        matchesPattern("/api/trades/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.accountId").value(PRIMARY_ACCOUNT_ID))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10.5))
                .andExpect(jsonPath("$.tradePrice").value(195.25))
                .andExpect(jsonPath("$.executedAt")
                        .value("2026-07-28T06:30:00Z"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-07-28T06:30:30Z"));

        mockMvc.perform(get("/api/trades?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void rejectsSellAndDoesNotPersistIt() throws Exception {
        expectValidationProblem(
                mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(
                                "SELL",
                                "2026-07-28T06:30:00Z"))),
                "side",
                "only BUY trades are supported");

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsInvalidTickerAndZeroQuantityWithoutPersisting() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "00000000-0000-0000-0000-000000000001",
                                  "ticker": "bad ticker!",
                                  "side": "BUY",
                                  "quantity": 0,
                                  "tradePrice": 1,
                                  "executedAt": "2026-07-28T06:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.ticker")
                        .value("must match [A-Z][A-Z0-9.-]{0,9}"))
                .andExpect(jsonPath("$.errors.quantity")
                        .value("must be greater than 0"));

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsExcessPrecisionAndFutureExecution() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "00000000-0000-0000-0000-000000000001",
                                  "ticker": "AAPL",
                                  "side": "BUY",
                                  "quantity": 1.0000001,
                                  "tradePrice": 10.0000001,
                                  "executedAt": "2026-07-28T06:31:31Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.quantity")
                        .value("must have at most 6 decimal places"))
                .andExpect(jsonPath("$.errors.tradePrice")
                        .value("must have at most 6 decimal places"))
                .andExpect(jsonPath("$.errors.executedAt")
                        .value("must not be more than 60 seconds in the future"));
    }

    @Test
    void returnsTradesInExecutionOrderWithPagination() throws Exception {
        book("AAPL", "2026-07-28T06:27:00Z");
        book("MSFT", "2026-07-28T06:29:00Z");
        book("BRK.B", "2026-07-28T06:28:00Z");

        mockMvc.perform(get("/api/trades?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].ticker").value("MSFT"))
                .andExpect(jsonPath("$.items[1].ticker").value("BRK.B"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/trades?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"));
    }

    @Test
    void validatesPaginationParameters() throws Exception {
        expectValidationProblem(
                mockMvc.perform(get("/api/trades?page=0&size=101")),
                "size",
                "must be between 1 and 100");
    }

    private void book(String ticker, String executedAt) throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "ticker": "%s",
                                  "side": "BUY",
                                  "quantity": 1,
                                  "tradePrice": 10,
                                  "executedAt": "%s"
                                }
                                """.formatted(
                                PRIMARY_ACCOUNT_ID, ticker, executedAt)))
                .andExpect(status().isCreated());
    }

    private String validRequest(String side, String executedAt) {
        return """
                {
                  "accountId": "%s",
                  "ticker": "AAPL",
                  "side": "%s",
                  "quantity": 1,
                  "tradePrice": 10,
                  "executedAt": "%s"
                }
                """.formatted(PRIMARY_ACCOUNT_ID, side, executedAt);
    }

    private void expectValidationProblem(
            org.springframework.test.web.servlet.ResultActions result,
            String field,
            String message) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:equity-trade:problem:validation"))
                .andExpect(jsonPath("$.title")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("One or more fields are invalid."))
                .andExpect(jsonPath("$.instance").value("/api/trades"))
                .andExpect(jsonPath("$.errors." + field).value(message))
                .andExpect(content().string(not(containsString("java."))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("SELECT "))))
                .andExpect(content().string(not(containsString("INSERT "))));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
