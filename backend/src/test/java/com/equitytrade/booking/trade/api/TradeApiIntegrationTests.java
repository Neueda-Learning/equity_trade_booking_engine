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
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void booksNormalizedBuyTradeAndListsIt() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("SELL", "2026-07-28T06:30:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("side"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("only BUY trades are supported"));

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsFutureExecutionAndInvalidAmounts() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "bad ticker!",
                                  "side": "BUY",
                                  "quantity": 0,
                                  "tradePrice": 1.0000001,
                                  "executedAt": "2026-07-28T06:31:31Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(4)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("ticker"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("quantity"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("tradePrice"))
                .andExpect(jsonPath("$.fieldErrors[3].field").value("executedAt"));
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
        mockMvc.perform(get("/api/trades?page=-1&size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
    }

    private void book(String ticker, String executedAt) throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "%s",
                                  "side": "BUY",
                                  "quantity": 1,
                                  "tradePrice": 10,
                                  "executedAt": "%s"
                                }
                                """.formatted(ticker, executedAt)))
                .andExpect(status().isCreated());
    }

    private String validRequest(String side, String executedAt) {
        return """
                {
                  "ticker": "AAPL",
                  "side": "%s",
                  "quantity": 1,
                  "tradePrice": 10,
                  "executedAt": "%s"
                }
                """.formatted(side, executedAt);
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
