package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinnhubInstrumentSearchProviderTests {

    private static final String TOKEN = "instrument-search-secret";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> requestPath =
            new AtomicReference<>();
    private final AtomicReference<String> requestQuery =
            new AtomicReference<>();
    private final AtomicReference<String> receivedToken =
            new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = """
            {
              "count": 5,
              "result": [
                {"description":"Apple Inc","displaySymbol":"AAPL",
                 "symbol":"AAPL","type":"Common Stock"},
                {"description":"Apple duplicate","displaySymbol":"AAPL",
                 "symbol":"AAPL","type":"Common Stock"},
                {"description":"SPDR S&P 500","displaySymbol":"SPY",
                 "symbol":"SPY","type":"ETP"},
                {"description":"Apple option","displaySymbol":"AAPL240",
                 "symbol":"AAPL240","type":"Option"},
                {"description":"Foreign listing","displaySymbol":"TOO-LONG-TICKER",
                 "symbol":"TOO-LONG-TICKER","type":"Common Stock"}
              ]
            }
            """;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", this::respond);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "finnhub-instrument-test-stub");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsFiltersAndDeduplicatesUsSecuritiesWithSafeHeader() {
        assertThat(provider(2).search("apple inc", 10))
                .extracting(result -> result.ticker())
                .containsExactly("AAPL", "SPY");
        assertThat(provider(2).search("apple inc", 10).get(1).type())
                .isEqualTo("ETF");
        assertThat(requestPath).hasValue("/search");
        assertThat(requestQuery.get())
                .contains("q=apple+inc")
                .contains("exchange=US")
                .doesNotContain(TOKEN);
        assertThat(receivedToken).hasValue(TOKEN);
    }

    @Test
    void malformedResponseIsNotRetried() {
        responseBody = "{\"count\":";

        assertFailure(MarketDataFailureCategory.MALFORMED_RESPONSE, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429})
    void clientFailuresAreNotRetried(int status) {
        responseStatus = status;

        assertFailure(
                status == 429
                        ? MarketDataFailureCategory.RATE_LIMIT
                        : status == 401 || status == 403
                                ? MarketDataFailureCategory.AUTHENTICATION
                                : MarketDataFailureCategory.NOT_FOUND,
                1);
    }

    @Test
    void serverFailureRetriesOnce() {
        responseStatus = 500;

        assertFailure(MarketDataFailureCategory.SERVER_ERROR, 2);
    }

    private void assertFailure(
            MarketDataFailureCategory category,
            int expectedCalls) {
        assertThatThrownBy(() -> provider(2).search("AAPL", 10))
                .isInstanceOf(MarketDataProviderException.class)
                .extracting(exception ->
                        ((MarketDataProviderException) exception).category())
                .isEqualTo(category);
        assertThat(calls).hasValue(expectedCalls);
    }

    private FinnhubInstrumentSearchProvider provider(int attempts) {
        return new FinnhubInstrumentSearchProvider(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(100))
                        .build(),
                new ObjectMapper(),
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()),
                TOKEN,
                Duration.ofSeconds(1),
                attempts);
    }

    private void respond(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        requestPath.set(exchange.getRequestURI().getPath());
        requestQuery.set(exchange.getRequestURI().getRawQuery());
        receivedToken.set(exchange.getRequestHeaders()
                .getFirst("X-Finnhub-Token"));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json");
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
