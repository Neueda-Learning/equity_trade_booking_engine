package com.equitytrade.booking.marketdata;

import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.equitytrade.booking.marketdata.infrastructure.redis.RedisMarketDataCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarketDataRedisMySqlIT {

    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";

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
        registry.add("market-data.provider", () -> "mock");
        registry.add("market-data.fresh-ttl", () -> "60s");
        registry.add("market-data.retention-ttl", () -> "24h");
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
    void clearTestData() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbcTemplate.update("DELETE FROM market_quote_snapshots");
        jdbcTemplate.update("DELETE FROM trades");
    }

    @Test
    void storesJsonWithRetentionTtlAndReturnsFreshCacheHit()
            throws Exception {
        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.source").value("MOCK"))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.stale").value(false));
        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));

        String key = RedisMarketDataCache.key("AAPL");
        String json = redisTemplate.opsForValue().get(key);
        Long ttlSeconds = redisTemplate.getExpire(key);
        JsonNode stored = objectMapper.readTree(json);

        assertThat(stored.path("ticker").asText()).isEqualTo("AAPL");
        assertThat(stored.path("price").decimalValue()).isPositive();
        assertThat(stored.path("previousClose").decimalValue()).isPositive();
        assertThat(stored.path("source").asText()).isEqualTo("MOCK");
        assertThat(stored.path("mock").asBoolean()).isTrue();
        assertThat(ttlSeconds).isGreaterThan(Duration.ofHours(23).toSeconds());
        assertThat(ttlSeconds)
                .isLessThanOrEqualTo(Duration.ofHours(24).toSeconds());
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM market_quote_snapshots
                        WHERE ticker = 'AAPL'
                        """,
                Integer.class)).isEqualTo(1);
    }

    @Test
    void isolatesTickerKeysAndRefreshOverwritesCachedJson()
            throws Exception {
        mockMvc.perform(get("/api/market-data/quotes/AAPL"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/market-data/quotes/MSFT"))
                .andExpect(status().isOk());

        String aaplKey = RedisMarketDataCache.key("AAPL");
        String msftKey = RedisMarketDataCache.key("MSFT");
        assertThat(redisTemplate.hasKey(aaplKey)).isTrue();
        assertThat(redisTemplate.hasKey(msftKey)).isTrue();

        MarketQuote fake = new MarketQuote(
                "AAPL",
                BigDecimal.ONE,
                BigDecimal.ONE,
                Instant.EPOCH,
                Instant.EPOCH,
                "MOCK",
                true);
        redisTemplate.opsForValue().set(
                aaplKey,
                objectMapper.writeValueAsString(fake));

        mockMvc.perform(post("/api/market-data/quotes/AAPL/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(195.25))
                .andExpect(jsonPath("$.cached").value(false));

        JsonNode replaced = objectMapper.readTree(
                redisTemplate.opsForValue().get(aaplKey));
        assertThat(replaced.path("price").decimalValue())
                .isEqualByComparingTo("195.25");
        assertThat(redisTemplate.getExpire(aaplKey))
                .isGreaterThan(Duration.ofHours(23).toSeconds());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_quote_snapshots",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void batchReturnsOnlyCurrentBookedPositionTickers()
            throws Exception {
        book("AAPL", "BUY", "10");
        book("MSFT", "BUY", "4");
        book("MSFT", "SELL", "4");

        mockMvc.perform(get("/api/market-data/quotes")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"));
    }

    @Test
    void clearingRedisRegeneratesQuoteAsAnUncachedResponse()
            throws Exception {
        mockMvc.perform(get("/api/market-data/quotes/GOOGL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
        mockMvc.perform(get("/api/market-data/quotes/GOOGL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        mockMvc.perform(get("/api/market-data/quotes/GOOGL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));
    }

    @Test
    void accountAndTradeRemainInMySqlAndNeverBecomeRedisRecords()
            throws Exception {
        book("NVDA", "BUY", "3");
        mockMvc.perform(get("/api/market-data/quotes")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticker").value("NVDA"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ?",
                Integer.class,
                PRIMARY_ACCOUNT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trades WHERE ticker = 'NVDA'",
                Integer.class)).isEqualTo(1);
        Set<String> keys = redisTemplate.keys("*");
        assertThat(keys)
                .isNotNull()
                .isNotEmpty()
                .allMatch(key -> key.startsWith("market:quote:"));
    }

    private void book(String ticker, String side, String quantity)
            throws Exception {
        MvcResult ignored = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", PRIMARY_ACCOUNT_ID,
                                "ticker", ticker,
                                "side", side,
                                "quantity", new BigDecimal(quantity),
                                "tradePrice", new BigDecimal("100"),
                                "executedAt",
                                Instant.now().minusSeconds(5)))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(ignored.getResponse().getContentAsString()).isNotBlank();
    }
}
