package com.equitytrade.booking.documentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "dashboard.snapshots.scheduling-enabled=false",
        "market-data.provider=mock"
})
class OpenApiSmokeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatesCompleteOpenApiDocumentWithoutSecrets() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(json);
        List<String> expectedPaths = List.of(
                "/api/accounts",
                "/api/accounts/{id}",
                "/api/accounts/{id}/deactivate",
                "/api/accounts/{accountId}/positions",
                "/api/trades",
                "/api/trades/{id}",
                "/api/trades/{id}/cancel",
                "/api/trades/{id}/amend",
                "/api/positions",
                "/api/market-data/instruments/search",
                "/api/market-data/quotes",
                "/api/market-data/quotes/{ticker}",
                "/api/market-data/quotes/{ticker}/refresh",
                "/api/market-data/provider/status",
                "/api/demo/market-data/outage",
                "/api/demo/market-data/outage/enable",
                "/api/demo/market-data/outage/disable",
                "/api/pnl",
                "/api/dashboard",
                "/api/dashboard/refresh",
                "/api/dashboard/history");

        expectedPaths.forEach(path ->
                assertThat(document.path("paths").has(path))
                        .as("OpenAPI path %s", path)
                        .isTrue());
        assertThat(document.path("components").path("schemas")
                .has("ProblemDetails")).isTrue();
        assertThat(json)
                .contains("MOCK")
                .contains("LIVE")
                .contains("CACHED")
                .contains("STALE")
                .contains("Demo Only")
                .doesNotContain("X-Finnhub-Token")
                .doesNotContain("FINNHUB_API_KEY")
                .doesNotContain("ci-finnhub-dummy-token");
    }

    @Test
    void exposesSwaggerUiEntryPoint() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        "/swagger-ui/index.html"));
    }
}
