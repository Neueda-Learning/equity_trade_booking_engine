package com.equitytrade.booking.trade.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TradeLifecyclePositionIntegrationTests.FixedClockConfiguration.class)
@SpringBootTest
@Transactional
class TradeLifecyclePositionIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void calculatesWeightedAverageAndReducesCostBasisOnSell()
            throws Exception {
        trade(PRIMARY_ACCOUNT_ID, "MSFT", "BUY", "10", "10",
                "2026-07-28T07:00:00Z", 201);
        trade(PRIMARY_ACCOUNT_ID, "MSFT", "BUY", "10", "20",
                "2026-07-28T07:10:00Z", 201);

        mockMvc.perform(get("/api/accounts/{id}/positions",
                        PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("MSFT"))
                .andExpect(jsonPath("$[0].quantity").value(20))
                .andExpect(jsonPath("$[0].averageCost").value(15))
                .andExpect(jsonPath("$[0].costBasis").value(300));

        trade(PRIMARY_ACCOUNT_ID, "MSFT", "SELL", "4", "30",
                "2026-07-28T07:20:00Z", 201);

        mockMvc.perform(get("/api/positions")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(16))
                .andExpect(jsonPath("$[0].averageCost").value(15))
                .andExpect(jsonPath("$[0].costBasis").value(240));
    }

    @Test
    void rejectsOversellAndBackdatedSellWithoutPersistence()
            throws Exception {
        trade(PRIMARY_ACCOUNT_ID, "AAPL", "BUY", "10", "10",
                "2026-07-28T07:20:00Z", 201);

        expectPositionConflict(tradeResult(
                PRIMARY_ACCOUNT_ID, "AAPL", "SELL", "11", "20",
                "2026-07-28T07:30:00Z"));
        expectPositionConflict(tradeResult(
                PRIMARY_ACCOUNT_ID, "AAPL", "SELL", "1", "20",
                "2026-07-28T07:10:00Z"));

        mockMvc.perform(get("/api/trades")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancelsIdempotentlyAndRestoresPositionAfterCancellingSell()
            throws Exception {
        String buyId = trade(PRIMARY_ACCOUNT_ID, "NVDA", "BUY", "10", "50",
                "2026-07-28T07:00:00Z", 201);
        String sellId = trade(PRIMARY_ACCOUNT_ID, "NVDA", "SELL", "4", "60",
                "2026-07-28T07:10:00Z", 201);

        mockMvc.perform(post("/api/trades/{id}/cancel", buyId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.quantity",
                        containsString("available at execution time")));

        MvcResult cancelledSell = mockMvc.perform(
                        post("/api/trades/{id}/cancel", sellId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andReturn();
        String firstCancelledAt = objectMapper.readTree(
                cancelledSell.getResponse().getContentAsString())
                .path("cancelledAt").asText();
        mockMvc.perform(post("/api/trades/{id}/cancel", sellId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledAt").value(firstCancelledAt));

        mockMvc.perform(get("/api/positions")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(10));

        mockMvc.perform(post("/api/trades/{id}/cancel", buyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/positions")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void isolatesAccountsAndAggregatesSameTickerInTickerOrder()
            throws Exception {
        String secondAccount = createAccount();
        trade(PRIMARY_ACCOUNT_ID, "MSFT", "BUY", "2", "20",
                "2026-07-28T07:00:00Z", 201);
        trade(PRIMARY_ACCOUNT_ID, "AAPL", "BUY", "2", "10",
                "2026-07-28T07:01:00Z", 201);
        trade(secondAccount, "AAPL", "BUY", "3", "20",
                "2026-07-28T07:02:00Z", 201);

        mockMvc.perform(get("/api/accounts/{id}/positions", secondAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountId").value(secondAccount))
                .andExpect(jsonPath("$[0].quantity").value(3));

        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].accountId").doesNotExist())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].costBasis").value(80))
                .andExpect(jsonPath("$[0].averageCost").value(16))
                .andExpect(jsonPath("$[1].ticker").value("MSFT"));
    }

    @Test
    void returnsProblemDetailsForUnknownAndMalformedCancellation()
            throws Exception {
        mockMvc.perform(post(
                        "/api/trades/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.id").value("does not exist"));

        mockMvc.perform(post("/api/trades/not-a-uuid/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.id").value("must be a valid UUID"));
    }

    private String createAccount() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Second",
                                "broker", "Broker",
                                "accountNumberLast4", "2468"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id").asText();
    }

    private String trade(
            String accountId,
            String ticker,
            String side,
            String quantity,
            String price,
            String executedAt,
            int expectedStatus) throws Exception {
        MvcResult result = tradeResult(
                accountId, ticker, side, quantity, price, executedAt)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return expectedStatus == 201
                ? objectMapper.readTree(
                        result.getResponse().getContentAsString())
                        .path("id").asText()
                : null;
    }

    private org.springframework.test.web.servlet.ResultActions tradeResult(
            String accountId,
            String ticker,
            String side,
            String quantity,
            String price,
            String executedAt) throws Exception {
        return mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "accountId", accountId,
                        "ticker", ticker,
                        "side", side,
                        "quantity", new BigDecimal(quantity),
                        "tradePrice", new BigDecimal(price),
                        "executedAt", Instant.parse(executedAt)))));
    }

    private void expectPositionConflict(
            org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:equity-trade:problem:conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errors.quantity",
                        containsString("available at execution time")));
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
