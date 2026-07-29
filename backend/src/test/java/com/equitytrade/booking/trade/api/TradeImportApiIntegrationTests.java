package com.equitytrade.booking.trade.api;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeImportApiIntegrationTests {

    private static final String HASH =
            "0123456789abcdef0123456789abcdef"
                    + "0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void requiresConfirmationBeforeRepeatingARegisteredTable()
            throws Exception {
        MvcResult first = mockMvc.perform(post(
                                "/api/trade-imports/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importCount").value(1))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();
        JsonNode firstBody = objectMapper.readTree(
                first.getResponse().getContentAsString());
        String importId = firstBody.path("importId").asText();
        assertThat(importId).isNotBlank();

        mockMvc.perform(post("/api/trade-imports/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(false)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:equity-trade:problem:conflict"))
                .andExpect(jsonPath("$.errors.contentHash")
                        .value("has already been imported"))
                .andExpect(jsonPath("$.duplicateImport.importId")
                        .value(importId))
                .andExpect(jsonPath("$.duplicateImport.importCount")
                        .value(1));

        mockMvc.perform(post("/api/trade-imports/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importId").value(importId))
                .andExpect(jsonPath("$.importCount").value(2));

        mockMvc.perform(patch(
                                "/api/trade-imports/{importId}/result",
                                importId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "importCount", 2,
                                "successCount", 1,
                                "failureCount", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.lastSuccessCount").value(1))
                .andExpect(jsonPath("$.lastFailureCount").value(1));
    }

    @Test
    void rejectsAnInvalidHashAsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/trade-imports/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentHash", "not-a-hash",
                                "fileName", "trades.csv",
                                "rowCount", 1,
                                "repeatConfirmed", false))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.contentHash")
                        .value("must be a lowercase SHA-256 hash"));
    }

    private String registration(boolean repeatConfirmed) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "contentHash", HASH,
                "fileName", "trades.csv",
                "rowCount", 2,
                "repeatConfirmed", repeatConfirmed));
    }
}
