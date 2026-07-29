package com.equitytrade.booking.pnl;

import com.equitytrade.booking.marketdata.domain.MarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketQuote;
import com.equitytrade.booking.marketdata.infrastructure.provider.DeterministicMockMarketDataProvider;
import com.equitytrade.booking.marketdata.infrastructure.redis.RedisMarketDataCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(PnlDashboardMySqlRedisIT.ProviderConfiguration.class)
class PnlDashboardMySqlRedisIT {

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
        registry.add(
                "dashboard.snapshots.scheduling-enabled",
                () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @Order(1)
    void v5CreatesSnapshotTableForeignKeyAndIndexInMySql84() {
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '5' AND success = 1
                        """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'valuation_snapshots'
                        """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = DATABASE()
                          AND constraint_name =
                            'fk_valuation_snapshots_account'
                        """,
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'valuation_snapshots'
                          AND index_name =
                            'idx_valuation_snapshots_scope_account_captured'
                        """,
                Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'valuation_snapshots'
                          AND column_name = 'captured_at'
                          AND datetime_precision = 6
                        """,
                Integer.class)).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void freshAndCachedQuotesCalculatePnlAndRefreshPersistsSnapshots()
            throws Exception {
        book("AAPL", "10", "100");

        mockMvc.perform(get("/api/pnl")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].marketPrice")
                        .value(195.25))
                .andExpect(jsonPath("$.items[0].marketValue")
                        .value(1952.5))
                .andExpect(jsonPath("$.items[0].unrealizedPnl")
                        .value(952.5))
                .andExpect(jsonPath("$.items[0].pnlPercent")
                        .value(95.25))
                .andExpect(jsonPath("$.items[0].cached").value(false));
        mockMvc.perform(get("/api/pnl")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cached").value(true));

        mockMvc.perform(post("/api/dashboard/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalCostBasis")
                        .value(1000))
                .andExpect(jsonPath("$.totals.totalMarketValue")
                        .value(1952.5))
                .andExpect(jsonPath("$.totals.totalUnrealizedPnl")
                        .value(952.5));
        mockMvc.perform(post("/api/dashboard/refresh"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM valuation_snapshots
                        WHERE scope_type = 'ALL'
                        """,
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM valuation_snapshots
                        WHERE scope_type = 'ACCOUNT'
                          AND account_id = ?
                        """,
                Integer.class,
                PRIMARY_ACCOUNT_ID)).isEqualTo(2);

        mockMvc.perform(get("/api/dashboard/history")
                        .param("range", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].valuationDate").exists());
        mockMvc.perform(get("/api/dashboard/history")
                        .param("accountId", PRIMARY_ACCOUNT_ID)
                        .param("range", "30D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].scopeType")
                        .value("ACCOUNT"));
    }

    @Test
    @Order(3)
    void historySurvivesApplicationRestartAndRedisFlush()
            throws Exception {
        mockMvc.perform(get("/api/dashboard/history")
                        .param("range", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        assertThat(redisTemplate.keys("market:quote:*")).isEmpty();

        mockMvc.perform(get("/api/dashboard/history")
                        .param("range", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].complete").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM valuation_snapshots",
                Integer.class)).isEqualTo(4);
    }

    @Test
    @Order(4)
    void staleAndMissingTickerProduceAPartialDashboard()
            throws Exception {
        book("MSFT", "2", "100");
        book("FAIL", "3", "50");
        MarketQuote stale = new MarketQuote(
                "MSFT",
                new BigDecimal("400"),
                new BigDecimal("390"),
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(120),
                "MOCK",
                true);
        redisTemplate.opsForValue().set(
                RedisMarketDataCache.key("MSFT"),
                objectMapper.writeValueAsString(stale),
                Duration.ofHours(24));
        ProviderConfiguration.FAIL_TICKERS.add("MSFT");
        ProviderConfiguration.FAIL_TICKERS.add("FAIL");
        try {
            mockMvc.perform(get("/api/dashboard")
                            .param("accountId", PRIMARY_ACCOUNT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(
                            "$.positions[?(@.ticker == 'MSFT')].stale")
                            .value(true))
                    .andExpect(jsonPath(
                            "$.positions[?(@.ticker == 'MSFT')].available")
                            .value(true))
                    .andExpect(jsonPath(
                            "$.positions[?(@.ticker == 'FAIL')].available")
                            .value(false))
                    .andExpect(jsonPath(
                            "$.totals.unpricedPositionCount").value(1))
                    .andExpect(jsonPath("$.totals.complete").value(false))
                    .andExpect(jsonPath("$.totals.stale").value(true));
        } finally {
            ProviderConfiguration.FAIL_TICKERS.clear();
        }
    }

    private void book(
            String ticker,
            String quantity,
            String price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", PRIMARY_ACCOUNT_ID,
                                "ticker", ticker,
                                "side", "BUY",
                                "quantity", new BigDecimal(quantity),
                                "tradePrice", new BigDecimal(price),
                                "executedAt",
                                Instant.now().minusSeconds(5)))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isNotBlank();
    }

    @TestConfiguration
    static class ProviderConfiguration {

        static final Set<String> FAIL_TICKERS =
                ConcurrentHashMap.newKeySet();

        @Bean
        @Primary
        MarketDataProvider controllableMarketDataProvider() {
            DeterministicMockMarketDataProvider delegate =
                    new DeterministicMockMarketDataProvider(
                            Clock.systemUTC(),
                            Duration.ofSeconds(60));
            return ticker -> {
                if (FAIL_TICKERS.contains(ticker)) {
                    throw new MarketDataProviderException("offline");
                }
                return delegate.fetch(ticker);
            };
        }
    }
}
