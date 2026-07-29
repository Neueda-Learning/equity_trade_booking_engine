package com.equitytrade.booking.pnl.api;

import com.equitytrade.booking.pnl.domain.PnlQuote;
import com.equitytrade.booking.pnl.domain.PnlQuoteSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class PnlDashboardApiIntegrationTests {

    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";

    @MockitoBean
    private PnlQuoteSource quoteSource;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void calculatesWeightedBuySellAndCancelledTradePnl()
            throws Exception {
        stubQuote("MSFT", "20", true, false);
        trade(PRIMARY_ACCOUNT_ID, "MSFT", "BUY", "10", "10", 30);
        trade(PRIMARY_ACCOUNT_ID, "MSFT", "BUY", "10", "20", 20);
        String sellId = trade(
                PRIMARY_ACCOUNT_ID,
                "MSFT",
                "SELL",
                "4",
                "25",
                10);

        mockMvc.perform(get("/api/pnl")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(16))
                .andExpect(jsonPath("$.items[0].averageCost").value(15))
                .andExpect(jsonPath("$.items[0].costBasis").value(240))
                .andExpect(jsonPath("$.items[0].marketValue").value(320))
                .andExpect(jsonPath("$.items[0].unrealizedPnl").value(80))
                .andExpect(jsonPath("$.items[0].pnlPercent")
                        .value(33.333333))
                .andExpect(jsonPath("$.items[0].cached").value(true));

        mockMvc.perform(post("/api/trades/{id}/cancel", sellId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pnl")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(20))
                .andExpect(jsonPath("$.items[0].costBasis").value(300))
                .andExpect(jsonPath("$.items[0].marketValue").value(400))
                .andExpect(jsonPath("$.items[0].unrealizedPnl").value(100));
    }

    @Test
    void isolatesAccountsAndAggregatesTheSameTicker()
            throws Exception {
        String secondAccount = createAccount("Second PnL");
        stubQuote("AAPL", "30", false, false);
        trade(PRIMARY_ACCOUNT_ID, "AAPL", "BUY", "2", "10", 20);
        trade(secondAccount, "AAPL", "BUY", "3", "20", 10);

        mockMvc.perform(get("/api/pnl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].accountId").doesNotExist())
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].costBasis").value(80))
                .andExpect(jsonPath("$.items[0].marketValue").value(150))
                .andExpect(jsonPath("$.items[0].unrealizedPnl").value(70));

        mockMvc.perform(get("/api/pnl")
                        .param("accountId", secondAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accountId")
                        .value(secondAccount))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].costBasis").value(60));
    }

    @Test
    void representsMissingQuoteAsNullAndIncomplete()
            throws Exception {
        stubQuote("AAPL", "110", true, true);
        when(quoteSource.find("MISS")).thenReturn(Optional.empty());
        trade(PRIMARY_ACCOUNT_ID, "AAPL", "BUY", "10", "100", 20);
        trade(PRIMARY_ACCOUNT_ID, "MISS", "BUY", "2", "50", 10);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalCostBasis")
                        .value(1000))
                .andExpect(jsonPath("$.totals.totalMarketValue")
                        .value(1100))
                .andExpect(jsonPath("$.totals.unpricedPositionCount")
                        .value(1))
                .andExpect(jsonPath("$.totals.complete").value(false))
                .andExpect(jsonPath("$.totals.mock").value(true))
                .andExpect(jsonPath("$.totals.stale").value(true))
                .andExpect(jsonPath("$.quoteStatus.unavailable").value(1))
                .andExpect(jsonPath(
                        "$.positions[?(@.ticker == 'MISS')].available")
                        .value(false))
                .andExpect(jsonPath(
                        "$.positions[1].marketPrice")
                        .value(nullValue()))
                .andExpect(jsonPath(
                        "$.positions[1].marketValue")
                        .value(nullValue()));
    }

    @Test
    void refreshCreatesSnapshotsAndHistoryReadsLocalValuations()
            throws Exception {
        stubQuote("AAPL", "12", false, false);
        trade(PRIMARY_ACCOUNT_ID, "AAPL", "BUY", "10", "10", 10);

        mockMvc.perform(post("/api/dashboard/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalCostBasis").value(100))
                .andExpect(jsonPath("$.totals.totalMarketValue").value(120))
                .andExpect(jsonPath("$.recentActivity[0].ticker")
                        .value("AAPL"))
                .andExpect(jsonPath("$.recentActivity[0].createdAt")
                        .exists())
                .andExpect(jsonPath("$.capturedAt").exists());
        mockMvc.perform(post("/api/dashboard/refresh"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM valuation_snapshots
                        WHERE scope_type = 'ALL'
                          AND account_id IS NULL
                        """,
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM valuation_snapshots
                        WHERE scope_type = 'ACCOUNT'
                          AND account_id = ?
                        """,
                Integer.class,
                PRIMARY_ACCOUNT_ID)).isEqualTo(2);

        MvcResult history = mockMvc.perform(get("/api/dashboard/history")
                        .param("range", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("ALL"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].valuationDate")
                        .value(java.time.LocalDate.now(
                                java.time.ZoneOffset.UTC).toString()))
                .andReturn();
        JsonNode items = objectMapper.readTree(
                history.getResponse().getContentAsString()).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("complete").asBoolean()).isTrue();
        assertThat(Instant.parse(items.get(0).path("capturedAt").asText()))
                .isBeforeOrEqualTo(Instant.parse(
                        items.get(1).path("capturedAt").asText()));

        mockMvc.perform(get("/api/dashboard/history")
                        .param("accountId", PRIMARY_ACCOUNT_ID)
                        .param("range", "30D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].scopeType")
                        .value("ACCOUNT"));
    }

    @Test
    void validatesHistoryRangeAndAccountWithProblemDetails()
            throws Exception {
        mockMvc.perform(get("/api/dashboard/history")
                        .param("range", "1Y"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.range")
                        .value("must be one of 1D, 7D, 30D or ALL"));

        mockMvc.perform(get("/api/pnl")
                        .param(
                                "accountId",
                                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.accountId")
                        .value("does not exist"));
    }

    private void stubQuote(
            String ticker,
            String price,
            boolean cached,
            boolean stale) {
        PnlQuote quote = new PnlQuote(
                ticker,
                new BigDecimal(price),
                Instant.parse("2026-07-28T09:00:00Z"),
                "MOCK",
                true,
                cached,
                stale);
        when(quoteSource.find(ticker)).thenReturn(Optional.of(quote));
        when(quoteSource.refresh(ticker)).thenReturn(Optional.of(quote));
    }

    private String createAccount(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
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
            int secondsAgo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId,
                                "ticker", ticker,
                                "side", side,
                                "quantity", new BigDecimal(quantity),
                                "tradePrice", new BigDecimal(price),
                                "executedAt",
                                Instant.now().minusSeconds(secondsAgo)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id").asText();
    }
}
