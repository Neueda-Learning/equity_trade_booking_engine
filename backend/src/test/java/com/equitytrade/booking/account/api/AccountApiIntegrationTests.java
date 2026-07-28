package com.equitytrade.booking.account.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class AccountApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsGetsUpdatesAndIdempotentlyDeactivatesAccount() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("Retirement", "Fidelity", "4321")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Retirement"))
                .andExpect(jsonPath("$.broker").value("Fidelity"))
                .andExpect(jsonPath("$.accountNumberLast4").value("4321"))
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String id = objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(patch("/api/accounts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest(
                                "Long-term Retirement", "Schwab", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Long-term Retirement"))
                .andExpect(jsonPath("$.broker").value("Schwab"))
                .andExpect(jsonPath("$.accountNumberLast4").isEmpty());

        mockMvc.perform(post("/api/accounts/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(post("/api/accounts/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void rejectsInvalidAndDuplicateAccountFieldsWithProblemDetails()
            throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("", "", "12x4")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").value("is required"))
                .andExpect(jsonPath("$.errors.broker").value("is required"))
                .andExpect(jsonPath("$.errors.accountNumberLast4")
                        .value("must be exactly 4 digits"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest(
                                "Primary Account", "Other", "1111")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").value("already exists"));
    }

    @Test
    void rejectsMissingMalformedUnknownAndInactiveTradeAccount()
            throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountId").value("is required"));

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequestLiteral("\"not-a-uuid\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountId")
                        .value("must be a valid UUID"));

        String unknown = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(unknown)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.accountId").value("does not exist"));

        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest(
                                "Inactive Account", "Broker", "1111")))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(post("/api/accounts/{id}/deactivate", id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(id)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.accountId")
                        .value("account is inactive"));

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersTradeActivityByAccount() throws Exception {
        String primary = "00000000-0000-0000-0000-000000000001";
        MvcResult created = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequest("Trading", "IBKR", "9876")))
                .andExpect(status().isCreated())
                .andReturn();
        String trading = objectMapper.readTree(
                created.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(primary)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(trading)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/trades")
                        .param("accountId", trading)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].accountId").value(trading))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private String accountRequest(String name, String broker, String last4)
            throws Exception {
        return objectMapper.writeValueAsString(new AccountRequest(
                name, broker, last4));
    }

    private String tradeRequest(String accountId) {
        return tradeRequestLiteral(
                accountId == null ? "null" : "\"" + accountId + "\"");
    }

    private String tradeRequestLiteral(String accountId) {
        return """
                {
                  "accountId": %s,
                  "ticker": "AAPL",
                  "side": "BUY",
                  "quantity": 1,
                  "tradePrice": 10,
                  "executedAt": "2026-07-28T06:30:00Z"
                }
                """.formatted(accountId);
    }
}
