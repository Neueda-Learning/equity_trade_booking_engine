package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "market-data.provider=finnhub",
        "market-data.finnhub-api-key=dummy-integration-token",
        "market-data.finnhub-base-url=http://127.0.0.1:1",
        "market-data.max-attempts=1",
        "market-data.demo-controls-enabled=true"
})
class DemoMarketDataApiIntegrationTests {

    @MockitoBean
    private MarketDataCache cache;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void restoreProvider() throws Exception {
        mockMvc.perform(post("/api/demo/market-data/outage/disable"))
                .andExpect(status().isOk());
    }

    @Test
    void demoEnableAndDisableAreExplicitAndResetProviderStatus()
            throws Exception {
        when(cache.find(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/demo/market-data/outage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.demoOnly").value(true));

        mockMvc.perform(post("/api/demo/market-data/outage/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString(
                                "DEMO outage")));

        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("DEMO")))
                .andExpect(jsonPath("$.errors.provider")
                        .value("DEMO outage enabled"));

        mockMvc.perform(get("/api/market-data/provider/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("FINNHUB"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.demoControlsEnabled").value(true))
                .andExpect(jsonPath("$.demoOutageEnabled").value(true))
                .andExpect(jsonPath("$.lastFailureCategory")
                        .value("DEMO_OUTAGE"));

        mockMvc.perform(post("/api/demo/market-data/outage/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void providerOutageDoesNotAffectCoreBusinessApis()
            throws Exception {
        mockMvc.perform(post("/api/demo/market-data/outage/enable"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/trades")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk());
    }
}
