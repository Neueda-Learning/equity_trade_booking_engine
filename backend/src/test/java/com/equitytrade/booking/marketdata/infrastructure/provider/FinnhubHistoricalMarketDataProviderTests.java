package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.DailyMarketPrice;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinnhubHistoricalMarketDataProviderTests {

    private static final String TOKEN = "historical-test-token";

    private HttpServer server;
    private final AtomicReference<String> requestQuery =
            new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = """
            {"c":[195.25,197.5],"t":[1785196800,1785283200],"s":"ok"}
            """;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stock/candle", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void requestsDailyCandlesAndMapsCloses() {
        List<DailyMarketPrice> prices = provider().fetchDailyCloses(
                "AAPL",
                LocalDate.parse("2026-07-28"),
                LocalDate.parse("2026-07-29"));

        assertThat(prices).hasSize(2);
        assertThat(prices.get(0).ticker()).isEqualTo("AAPL");
        assertThat(prices.get(0).tradingDate())
                .isEqualTo("2026-07-28");
        assertThat(prices.get(0).close())
                .isEqualByComparingTo("195.25");
        assertThat(prices.get(0).source()).isEqualTo("FINNHUB");
        assertThat(prices.get(0).mock()).isFalse();
        assertThat(requestQuery.get())
                .contains("symbol=AAPL")
                .contains("resolution=D")
                .contains("from=")
                .contains("to=");
    }

    @Test
    void noDataReturnsAnEmptySeries() {
        responseBody = "{\"s\":\"no_data\"}";

        assertThat(provider().fetchDailyCloses(
                "AAPL",
                LocalDate.parse("2026-07-28"),
                LocalDate.parse("2026-07-29"))).isEmpty();
    }

    @Test
    void unauthorizedHistoricalAccessIsCategorized() {
        responseStatus = 403;

        assertThatThrownBy(() -> provider().fetchDailyCloses(
                "AAPL",
                LocalDate.parse("2026-07-28"),
                LocalDate.parse("2026-07-29")))
                .isInstanceOf(MarketDataProviderException.class)
                .extracting(exception ->
                        ((MarketDataProviderException) exception).category())
                .isEqualTo(MarketDataFailureCategory.AUTHENTICATION);
    }

    private FinnhubHistoricalMarketDataProvider provider() {
        return new FinnhubHistoricalMarketDataProvider(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(1))
                        .build(),
                new ObjectMapper(),
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()),
                TOKEN,
                Duration.ofSeconds(1),
                1);
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestQuery.set(exchange.getRequestURI().getRawQuery());
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json");
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
