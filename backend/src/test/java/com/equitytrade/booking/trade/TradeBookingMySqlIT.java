package com.equitytrade.booking.trade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
class TradeBookingMySqlIT {

    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";
    private static final String HISTORICAL_TRADE_ID =
            "10000000-0000-0000-0000-000000000001";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("equity_booking")
                    .withUsername("equity_app")
                    .withPassword("integration_password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) throws Exception {
        prepareHistoricalTradeAtV2();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void deleteTrades() {
        jdbcTemplate.update(
                "DELETE FROM trades WHERE id <> ?",
                HISTORICAL_TRADE_ID);
    }

    @Test
    void flywayCreatesExpectedMySqlSchema() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('1', '2', '3') AND success = 1
                        """,
                Integer.class);
        Integer accountTableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'accounts'
                        """,
                Integer.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = DATABASE()
                          AND constraint_name = 'fk_trades_account'
                        """,
                Integer.class);
        String historicalAccountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM trades WHERE id = ?",
                String.class,
                HISTORICAL_TRADE_ID);

        assertThat(successfulMigrations).isEqualTo(3);
        assertThat(accountTableCount).isEqualTo(1);
        assertThat(foreignKeyCount).isEqualTo(1);
        assertThat(historicalAccountId).isEqualTo(PRIMARY_ACCOUNT_ID);
    }

    @Test
    void booksAndReadsBuyTradeThroughMySql() throws Exception {
        BigDecimal quantity = new BigDecimal("1234567890123.123456");
        BigDecimal tradePrice = new BigDecimal("9876543210123.654321");
        Instant executedAt = Instant.now()
                .minusSeconds(5)
                .truncatedTo(ChronoUnit.MICROS);

        MvcResult createResult = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                " audit ",
                                "BUY",
                                quantity,
                                tradePrice,
                                executedAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("AUDIT"))
                .andExpect(jsonPath("$.accountId").value(PRIMARY_ACCOUNT_ID))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn();

        JsonNode created = readExactJson(
                createResult.getResponse().getContentAsString());
        UUID tradeId = UUID.fromString(created.path("id").asText());
        assertThat(created.path("quantity").decimalValue())
                .isEqualByComparingTo(quantity);
        assertThat(created.path("tradePrice").decimalValue())
                .isEqualByComparingTo(tradePrice);

        MvcResult listResult = mockMvc.perform(get(
                        "/api/trades?accountId={accountId}&page=0&size=10",
                        PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = findTrade(
                readExactJson(listResult.getResponse().getContentAsString())
                        .path("items"),
                tradeId);
        assertThat(listed.path("ticker").asText()).isEqualTo("AUDIT");
        assertThat(listed.path("quantity").decimalValue())
                .isEqualByComparingTo(quantity);
        assertThat(listed.path("tradePrice").decimalValue())
                .isEqualByComparingTo(tradePrice);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                        SELECT account_id, ticker, quantity, trade_price
                        FROM trades
                        WHERE id = ?
                        """,
                tradeId.toString());
        assertThat(stored.get("ticker")).isEqualTo("AUDIT");
        assertThat(stored.get("account_id")).isEqualTo(PRIMARY_ACCOUNT_ID);
        assertThat((BigDecimal) stored.get("quantity"))
                .isEqualByComparingTo(quantity);
        assertThat((BigDecimal) stored.get("trade_price"))
                .isEqualByComparingTo(tradePrice);
    }

    @Test
    void rejectsSellAndExcessPrecisionWithoutWritingToMySql()
            throws Exception {
        long initialCount = tradeCount();
        Instant executedAt = Instant.now().minusSeconds(5);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "AAPL",
                                "SELL",
                                BigDecimal.ONE,
                                BigDecimal.TEN,
                                executedAt)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.side")
                        .value("only BUY trades are supported"));

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "AAPL",
                                "BUY",
                                new BigDecimal("1.0000001"),
                                BigDecimal.TEN,
                                executedAt)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.quantity")
                        .value("must have at most 6 decimal places"));

        assertThat(tradeCount()).isEqualTo(initialCount);
    }

    @Test
    void persistsAccountsAndIsolatesActivityByAccount() throws Exception {
        String first = createAccount("Taxable", "IBKR", "1234");
        String second = createAccount("Retirement", "Schwab", "5678");

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                first,
                                "AAPL",
                                "BUY",
                                BigDecimal.ONE,
                                BigDecimal.TEN,
                                Instant.now().minusSeconds(5))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                second,
                                "MSFT",
                                "BUY",
                                BigDecimal.ONE,
                                BigDecimal.TEN,
                                Instant.now().minusSeconds(5))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(
                        "/api/trades?accountId={accountId}&page=0&size=20",
                        first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.items[0].accountId").value(first));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id IN (?, ?)",
                Integer.class,
                first,
                second)).isEqualTo(2);
    }

    private String tradeRequest(
            String accountId,
            String ticker,
            String side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            Instant executedAt) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "accountId", accountId,
                "ticker", ticker,
                "side", side,
                "quantity", quantity,
                "tradePrice", tradePrice,
                "executedAt", executedAt));
    }

    private long tradeCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trades",
                Long.class);
        return count == null ? 0 : count;
    }

    private String createAccount(String name, String broker, String last4)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "broker", broker,
                                "accountNumberLast4", last4))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).path("id").asText();
    }

    private static void prepareHistoricalTradeAtV2() throws Exception {
        Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword());
                PreparedStatement statement = connection.prepareStatement(
                        """
                                INSERT INTO trades (
                                    id, ticker, side, quantity, trade_price,
                                    executed_at, status, created_at
                                ) VALUES (?, 'LEGACY', 'BUY', 1.000000,
                                    10.000000, CURRENT_TIMESTAMP(6), 'BOOKED',
                                    CURRENT_TIMESTAMP(6))
                                """)) {
            statement.setString(1, HISTORICAL_TRADE_ID);
            statement.executeUpdate();
        }
    }

    private JsonNode readExactJson(String json) throws Exception {
        return objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(json);
    }

    private JsonNode findTrade(JsonNode items, UUID tradeId) {
        for (JsonNode item : items) {
            if (tradeId.toString().equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Trade not found in response: " + tradeId);
    }
}
