package com.equitytrade.booking.marketdata.infrastructure.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinnhubMarketDataProviderTests {

    private static final Instant NOW =
            Instant.parse("2026-07-28T10:00:00Z");
    private static final String TOKEN = "unit-test-secret-token";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> requestPath =
            new AtomicReference<>();
    private final AtomicReference<String> requestQuery =
            new AtomicReference<>();
    private final AtomicReference<String> receivedToken =
            new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody =
            "{\"c\":195.123456,\"pc\":193.8,\"t\":1785231000}";
    private volatile long responseDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quote", this::respond);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "finnhub-test-stub");
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
    void mapsQuoteWithExactDecimalsTimestampPathAndHeader() {
        MarketQuote quote = provider(Duration.ofSeconds(1), 2)
                .fetch("AAPL");

        assertThat(quote.price()).isEqualByComparingTo("195.123456");
        assertThat(quote.previousClose()).isEqualByComparingTo("193.8");
        assertThat(quote.marketTimestamp())
                .isEqualTo(Instant.ofEpochSecond(1_785_231_000L));
        assertThat(quote.fetchedAt()).isEqualTo(NOW);
        assertThat(quote.source()).isEqualTo("FINNHUB");
        assertThat(quote.mock()).isFalse();
        assertThat(requestPath).hasValue("/quote");
        assertThat(requestQuery).hasValue("symbol=AAPL");
        assertThat(receivedToken).hasValue(TOKEN);
        assertThat(requestQuery.get()).doesNotContain(TOKEN);
        assertThat(calls).hasValue(1);
    }

    @Test
    void zeroOrMissingQuoteIsNotFoundAndIsNotRetried() {
        responseBody = "{\"c\":0,\"pc\":0,\"t\":1785231000}";

        assertFailure(
                MarketDataFailureCategory.NOT_FOUND,
                1);

        calls.set(0);
        responseBody = "{\"c\":195.2,\"t\":1785231000}";
        assertFailure(
                MarketDataFailureCategory.NOT_FOUND,
                1);
    }

    @Test
    void malformedJsonAndInvalidTimestampAreNotRetried() {
        responseBody = "{\"c\":";
        assertFailure(
                MarketDataFailureCategory.MALFORMED_RESPONSE,
                1);

        calls.set(0);
        responseBody = "{\"c\":195.2,\"pc\":193.8,\"t\":0}";
        assertFailure(
                MarketDataFailureCategory.MALFORMED_RESPONSE,
                1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404})
    void missingTickerResponsesAreNotRetried(int status) {
        responseStatus = status;

        assertFailure(MarketDataFailureCategory.NOT_FOUND, 1);
    }

    @Test
    void rateLimitIsNotRetried() {
        responseStatus = 429;

        assertFailure(MarketDataFailureCategory.RATE_LIMIT, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authenticationFailureIsSafeAndNotRetried(int status) {
        responseStatus = status;
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                        FinnhubMarketDataProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertFailure(
                    MarketDataFailureCategory.AUTHENTICATION,
                    1);
            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(messages)
                    .isNotEmpty()
                    .allMatch(message ->
                            !message.contains(TOKEN)
                                    && !message.contains(
                                            "X-Finnhub-Token"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void serverFailureRetriesExactlyOnce() {
        responseStatus = 500;

        assertFailure(MarketDataFailureCategory.SERVER_ERROR, 2);
    }

    @Test
    void timeoutRetriesExactlyOnceWithinBound() {
        responseDelayMillis = 250;

        assertThatThrownBy(() ->
                provider(Duration.ofMillis(40), 2).fetch("AAPL"))
                .isInstanceOf(MarketDataProviderException.class)
                .extracting(exception ->
                        ((MarketDataProviderException) exception).category())
                .isEqualTo(MarketDataFailureCategory.TIMEOUT);
        assertThat(calls).hasValue(2);
    }

    @Test
    void oneAttemptDisablesRetry() {
        responseStatus = 500;

        assertThatThrownBy(() ->
                provider(Duration.ofSeconds(1), 1).fetch("AAPL"))
                .isInstanceOf(MarketDataProviderException.class);
        assertThat(calls).hasValue(1);
    }

    private void assertFailure(
            MarketDataFailureCategory category,
            int expectedCalls) {
        assertThatThrownBy(() ->
                provider(Duration.ofSeconds(1), 2).fetch("AAPL"))
                .isInstanceOf(MarketDataProviderException.class)
                .extracting(exception ->
                        ((MarketDataProviderException) exception).category())
                .isEqualTo(category);
        assertThat(calls).hasValue(expectedCalls);
    }

    private FinnhubMarketDataProvider provider(
            Duration timeout,
            int attempts) {
        return new FinnhubMarketDataProvider(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(100))
                        .build(),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()),
                TOKEN,
                timeout,
                attempts);
    }

    private void respond(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        requestPath.set(exchange.getRequestURI().getPath());
        requestQuery.set(exchange.getRequestURI().getRawQuery());
        receivedToken.set(exchange.getRequestHeaders()
                .getFirst("X-Finnhub-Token"));
        if (responseDelayMillis > 0) {
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
        } catch (IOException ignored) {
            // Expected when the client deliberately times out.
        } finally {
            exchange.close();
        }
    }
}
