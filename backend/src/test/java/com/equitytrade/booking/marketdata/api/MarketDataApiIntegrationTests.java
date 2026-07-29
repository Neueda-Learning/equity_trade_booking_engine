package com.equitytrade.booking.marketdata.api;

import com.equitytrade.booking.marketdata.domain.MarketDataCache;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class MarketDataApiIntegrationTests {

    @MockitoBean
    private MarketDataProvider provider;

    @MockitoBean
    private MarketDataCache cache;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidTickerReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/market-data/quotes/bad ticker"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.ticker")
                        .value("must match [A-Z][A-Z0-9.-]{0,9}"));
    }

    @Test
    void unknownAndMalformedAccountReturnProblemDetails() throws Exception {
        mockMvc.perform(get("/api/market-data/quotes")
                        .param(
                                "accountId",
                                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.accountId")
                        .value("does not exist"));

        mockMvc.perform(get("/api/market-data/quotes")
                        .param("accountId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountId")
                        .value("must be a valid UUID"));
    }

    @Test
    void providerFailureWithoutCacheReturns503ProblemDetails()
            throws Exception {
        when(cache.find(anyString())).thenReturn(Optional.empty());
        when(provider.fetch(anyString())).thenThrow(
                new MarketDataProviderException("offline"));

        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "urn:equity-trade:problem:market-data-unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.errors.ticker")
                        .value("market data is unavailable for AAPL"));
    }

    @Test
    void providerNotFoundReturns404ProblemDetails() throws Exception {
        when(cache.find(anyString())).thenReturn(Optional.empty());
        when(provider.fetch(anyString())).thenThrow(
                new MarketDataProviderException(
                        MarketDataFailureCategory.NOT_FOUND,
                        "no data"));

        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title")
                        .value("Market quote not found"))
                .andExpect(jsonPath("$.errors.ticker")
                        .value("no quote exists for AAPL"));
    }

    @Test
    void mockProviderStatusIsSafeAndDemoEndpointsAreHidden()
            throws Exception {
        mockMvc.perform(get("/api/market-data/provider/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("MOCK"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.demoControlsEnabled").value(false))
                .andExpect(jsonPath("$.demoOutageEnabled").value(false))
                .andExpect(jsonPath("$.lastSuccessAt").doesNotExist())
                .andExpect(jsonPath("$.lastFailureCategory").doesNotExist());

        mockMvc.perform(get("/api/demo/market-data/outage"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void searchesSupportedMockInstrumentsByTickerOrCompanyName()
            throws Exception {
        mockMvc.perform(get("/api/market-data/instruments/search")
                        .param("q", "apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.items[0].name").value("APPLE INC"))
                .andExpect(jsonPath("$.items[0].exchange").value("US"))
                .andExpect(jsonPath("$.items[0].type")
                        .value("Common Stock"));

        mockMvc.perform(get("/api/market-data/instruments/search")
                        .param("q", "not-listed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void validatesInstrumentSearchQueryAndLimitAsProblemDetails()
            throws Exception {
        mockMvc.perform(get("/api/market-data/instruments/search")
                        .param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.q").value("is required"));

        mockMvc.perform(get("/api/market-data/instruments/search")
                        .param("q", "AAPL")
                        .param("limit", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limit")
                        .value("must be between 1 and 20"));
    }
}
