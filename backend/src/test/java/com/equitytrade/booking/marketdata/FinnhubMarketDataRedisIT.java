package com.equitytrade.booking.marketdata;

import com.equitytrade.booking.marketdata.infrastructure.redis.RedisMarketDataCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinnhubMarketDataRedisIT {

    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";
    private static final String DUMMY_TOKEN = "integration-dummy-token";
    private static final FinnhubStub STUB = FinnhubStub.start();

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("equity_booking")
                    .withUsername("equity_app")
                    .withPassword("integration_password");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.2-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(6379));
        registry.add("market-data.provider", () -> "finnhub");
        registry.add(
                "market-data.finnhub-base-url",
                STUB::baseUrl);
        registry.add(
                "market-data.finnhub-api-key",
                () -> DUMMY_TOKEN);
        registry.add("market-data.max-attempts", () -> "2");
        registry.add("market-data.read-timeout", () -> "200ms");
        registry.add("market-data.fresh-ttl", () -> "60s");
        registry.add("market-data.retention-ttl", () -> "24h");
        registry.add(
                "dashboard.snapshots.scheduling-enabled",
                () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        STUB.success();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbcTemplate.update("DELETE FROM trades");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @Test
    void successfulFinnhubQuoteIsCachedAsJsonAndSecondReadIsCached()
            throws Exception {
        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(195.123456))
                .andExpect(jsonPath("$.previousClose").value(193.8))
                .andExpect(jsonPath("$.source").value("FINNHUB"))
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.stale").value(false));
        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.stale").value(false));

        assertThat(STUB.lastToken()).isEqualTo(DUMMY_TOKEN);
        assertThat(STUB.lastQuery()).isEqualTo("symbol=AAPL");
        assertThat(STUB.lastQuery()).doesNotContain(DUMMY_TOKEN);
        String cached = redisTemplate.opsForValue().get(
                RedisMarketDataCache.key("AAPL"));
        JsonNode json = objectMapper.readTree(cached);
        assertThat(json.path("source").asText()).isEqualTo("FINNHUB");
        assertThat(json.path("mock").asBoolean()).isFalse();
    }

    @Test
    void failureReturnsStaleRedisThenNoCacheReturns503AndRecoveryIsFresh()
            throws Exception {
        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk());
        STUB.serverError();
        STUB.resetCalls();

        mockMvc.perform(post("/api/market-data/quotes/AAPL/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FINNHUB"))
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.stale").value(true));
        assertThat(STUB.calls()).isEqualTo(2);

        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.stale").value(true));

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        mockMvc.perform(post("/api/market-data/quotes/AAPL/refresh"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.provider")
                        .value("provider server error"));

        STUB.success();
        mockMvc.perform(post("/api/market-data/quotes/AAPL/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FINNHUB"))
                .andExpect(jsonPath("$.mock").value(false))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void noDataIs404AndNeverWrittenToRedis() throws Exception {
        STUB.noData();

        mockMvc.perform(get("/api/market-data/quotes/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(redisTemplate.hasKey(
                RedisMarketDataCache.key("MISSING"))).isFalse();
        assertThat(STUB.calls()).isEqualTo(1);
    }

    @Test
    void providerOutageDoesNotAffectAccountActivityOrPosition()
            throws Exception {
        bookAapl();
        STUB.serverError();

        mockMvc.perform(post("/api/market-data/quotes/AAPL/refresh"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/trades")
                        .param("accountId", PRIMARY_ACCOUNT_ID)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"));
        mockMvc.perform(get("/api/positions")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    private void bookAapl() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", PRIMARY_ACCOUNT_ID,
                                "ticker", "AAPL",
                                "side", "BUY",
                                "quantity", 10,
                                "tradePrice", 100,
                                "executedAt",
                                Instant.now().minusSeconds(5)))))
                .andExpect(status().isCreated());
    }

    private static final class FinnhubStub {

        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastToken =
                new AtomicReference<>();
        private final AtomicReference<String> lastQuery =
                new AtomicReference<>();
        private volatile int status = 200;
        private volatile String body =
                "{\"c\":195.123456,\"pc\":193.8,\"t\":1785231000}";

        private FinnhubStub(HttpServer server) {
            this.server = server;
        }

        static FinnhubStub start() {
            try {
                HttpServer server = HttpServer.create(
                        new InetSocketAddress(0),
                        0);
                FinnhubStub stub = new FinnhubStub(server);
                server.createContext("/quote", stub::respond);
                server.setExecutor(Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "finnhub-it-stub");
                            thread.setDaemon(true);
                            return thread;
                        }));
                server.start();
                return stub;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not start Finnhub integration stub",
                        exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void success() {
            status = 200;
            body = "{\"c\":195.123456,\"pc\":193.8,\"t\":1785231000}";
            resetCalls();
        }

        void serverError() {
            status = 500;
            body = "{\"error\":\"stub unavailable\"}";
        }

        void noData() {
            status = 200;
            body = "{\"c\":0,\"pc\":0,\"t\":0}";
        }

        int calls() {
            return calls.get();
        }

        void resetCalls() {
            calls.set(0);
        }

        String lastToken() {
            return lastToken.get();
        }

        String lastQuery() {
            return lastQuery.get();
        }

        void stop() {
            server.stop(0);
        }

        private void respond(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            lastToken.set(exchange.getRequestHeaders()
                    .getFirst("X-Finnhub-Token"));
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
