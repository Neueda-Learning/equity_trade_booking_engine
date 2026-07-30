package com.equitytrade.booking.trade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradeBookingMySqlIT {

    private static final String PRIMARY_ACCOUNT_ID =
            "00000000-0000-0000-0000-000000000001";
    private static final String HISTORICAL_TRADE_ID =
            "10000000-0000-0000-0000-000000000001";
    private static final AtomicBoolean DATABASE_PREPARED = new AtomicBoolean();

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

    @Test
    @Order(1)
    void flywayCreatesExpectedMySqlSchema() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('1', '2', '3', '4', '5', '6', '7')
                          AND success = 1
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
        Integer positionIndexCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trades'
                          AND index_name = 'idx_trades_position_replay'
                        """,
                Integer.class);
        Integer cancelledColumnCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trades'
                          AND column_name = 'cancelled_at'
                          AND datetime_precision = 6
                        """,
                Integer.class);
        Integer lifecycleConstraintCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE constraint_schema = DATABASE()
                          AND table_name = 'trades'
                          AND constraint_type = 'CHECK'
                          AND constraint_name IN (
                            'chk_trades_side',
                            'chk_trades_status'
                          )
                        """,
                Integer.class);
        Integer auditColumnCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trades'
                          AND column_name IN (
                            'cancellation_reason',
                            'supersedes_trade_id'
                          )
                        """,
                Integer.class);
        Integer auditForeignKeyCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = DATABASE()
                          AND constraint_name = 'fk_trades_supersedes_trade'
                        """,
                Integer.class);
        Integer auditIndexCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trades'
                          AND index_name = 'uk_trades_supersedes_trade'
                        """,
                Integer.class);
        Integer importRegistryCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trade_import_registry'
                        """,
                Integer.class);
        Integer importHashIndexCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trade_import_registry'
                          AND index_name =
                            'uk_trade_import_registry_content_hash'
                        """,
                Integer.class);

        assertThat(successfulMigrations).isEqualTo(7);
        assertThat(accountTableCount).isEqualTo(1);
        assertThat(foreignKeyCount).isEqualTo(1);
        assertThat(historicalAccountId).isEqualTo(PRIMARY_ACCOUNT_ID);
        assertThat(positionIndexCount).isEqualTo(6);
        assertThat(cancelledColumnCount).isEqualTo(1);
        assertThat(lifecycleConstraintCount).isEqualTo(2);
        assertThat(auditColumnCount).isEqualTo(2);
        assertThat(auditForeignKeyCount).isEqualTo(1);
        assertThat(auditIndexCount).isEqualTo(1);
        assertThat(importRegistryCount).isEqualTo(1);
        assertThat(importHashIndexCount).isEqualTo(1);
    }

    @Test
    @Order(2)
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
    @Order(3)
    void rejectsOversellAndExcessPrecisionWithoutWritingToMySql()
            throws Exception {
        long initialCount = tradeCount();
        Instant executedAt = Instant.now().minusSeconds(5);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "NOSHRT",
                                "SELL",
                                BigDecimal.ONE,
                                BigDecimal.TEN,
                                executedAt)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.quantity").exists());

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "PRECISE",
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
    @Order(4)
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

    @Test
    @Order(5)
    void serializesConcurrentSellsWithAnAccountRowLock() throws Exception {
        Instant buyTime = Instant.now().minusSeconds(30);
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "CONCUR",
                                "BUY",
                                new BigDecimal("10"),
                                BigDecimal.TEN,
                                buyTime)))
                .andExpect(status().isCreated());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(
                    executor.submit(() -> concurrentSell(ready, start)),
                    executor.submit(() -> concurrentSell(ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = results.stream()
                    .map(result -> {
                        try {
                            return result.get(10, TimeUnit.SECONDS);
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .sorted()
                    .toList();

            assertThat(statuses).containsExactly(201, 409);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/positions")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticker == 'CONCUR')].quantity")
                        .value(2));
    }

    @Test
    @Order(6)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void persistsBuySellAndCancellationBeforeApplicationRestart()
            throws Exception {
        Instant buyTime = Instant.now().minusSeconds(30);
        MvcResult buy = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "RSTRT",
                                "BUY",
                                new BigDecimal("10"),
                                new BigDecimal("25"),
                                buyTime)))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult sell = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "RSTRT",
                                "SELL",
                                new BigDecimal("4"),
                                new BigDecimal("30"),
                                buyTime.plusSeconds(5))))
                .andExpect(status().isCreated())
                .andReturn();
        String sellId = objectMapper.readTree(
                sell.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(post("/api/trades/{id}/cancel", sellId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM trades
                        WHERE ticker = 'RSTRT'
                          AND side = 'SELL'
                          AND status = 'CANCELLED'
                          AND cancelled_at IS NOT NULL
                        """,
                Integer.class)).isEqualTo(1);
        assertThat(objectMapper.readTree(
                buy.getResponse().getContentAsString()).path("id").asText())
                .isNotBlank();
    }

    @Test
    @Order(7)
    void restoresPositionFromMySqlAfterApplicationContextRestart()
            throws Exception {
        mockMvc.perform(get("/api/positions")
                        .param("accountId", PRIMARY_ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticker == 'RSTRT')].quantity")
                        .value(10))
                .andExpect(jsonPath("$[?(@.ticker == 'RSTRT')].costBasis")
                        .value(250));
    }

    @Test
    @Order(8)
    void persistsAuditPreservedDeletionAndAmendmentInMySql()
            throws Exception {
        Instant executedAt = Instant.now().minusSeconds(20)
                .truncatedTo(ChronoUnit.MICROS);
        MvcResult deletedBuy = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "MISS",
                                "BUY",
                                new BigDecimal("2"),
                                new BigDecimal("11"),
                                executedAt)))
                .andExpect(status().isCreated())
                .andReturn();
        String deletedId = objectMapper.readTree(
                        deletedBuy.getResponse().getContentAsString())
                .path("id").asText();
        mockMvc.perform(delete("/api/trades/{id}", deletedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationReason").value("DELETED"));

        MvcResult originalBuy = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "FAIL",
                                "BUY",
                                new BigDecimal("3"),
                                new BigDecimal("12"),
                                executedAt)))
                .andExpect(status().isCreated())
                .andReturn();
        String originalId = objectMapper.readTree(
                        originalBuy.getResponse().getContentAsString())
                .path("id").asText();
        MvcResult amendment = mockMvc.perform(post(
                                "/api/trades/{id}/amend",
                                originalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "FAIL",
                                "BUY",
                                new BigDecimal("5"),
                                new BigDecimal("13"),
                                executedAt.plusSeconds(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledTrade.cancellationReason")
                        .value("AMENDED"))
                .andExpect(jsonPath("$.replacementTrade.supersedesTradeId")
                        .value(originalId))
                .andReturn();
        String replacementId = objectMapper.readTree(
                        amendment.getResponse().getContentAsString())
                .path("replacementTrade").path("id").asText();

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT cancellation_reason
                        FROM trades
                        WHERE id = ?
                        """,
                String.class,
                deletedId)).isEqualTo("DELETED");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT cancellation_reason
                        FROM trades
                        WHERE id = ?
                        """,
                String.class,
                originalId)).isEqualTo("AMENDED");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT supersedes_trade_id
                        FROM trades
                        WHERE id = ?
                        """,
                String.class,
                replacementId)).isEqualTo(originalId);
    }

    @Test
    @Order(9)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void persistsCsvImportIdentityAndResultInMySql() throws Exception {
        String contentHash =
                "11111111111111111111111111111111"
                        + "11111111111111111111111111111111";
        MvcResult registration = mockMvc.perform(post(
                                "/api/trade-imports/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentHash", contentHash,
                                "fileName", "mysql-import.csv",
                                "rowCount", 2,
                                "repeatConfirmed", false))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode registered = objectMapper.readTree(
                registration.getResponse().getContentAsString());
        String importId = registered.path("importId").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders.patch(
                                        "/api/trade-imports/{id}/result",
                                        importId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "importCount", 1,
                                "successCount", 2,
                                "failureCount", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                        SELECT content_hash, import_count, status,
                               last_success_count, last_failure_count
                        FROM trade_import_registry
                        WHERE id = ?
                        """,
                importId);
        assertThat(stored.get("content_hash")).isEqualTo(contentHash);
        assertThat(stored.get("import_count")).isEqualTo(1);
        assertThat(stored.get("status")).isEqualTo("COMPLETED");
        assertThat(stored.get("last_success_count")).isEqualTo(2);
        assertThat(stored.get("last_failure_count")).isEqualTo(0);
    }

    @Test
    @Order(10)
    void concurrentCsvRegistrationsAllowOnlyOneFirstImport() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM trade_import_registry
                        WHERE first_file_name = 'mysql-import.csv'
                          AND status = 'COMPLETED'
                        """,
                Integer.class)).isEqualTo(1);
        String contentHash =
                "abcdef0123456789abcdef0123456789"
                        + "abcdef0123456789abcdef0123456789";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() ->
                    concurrentImportRegistration(
                            contentHash,
                            ready,
                            start));
            Future<Integer> second = executor.submit(() ->
                    concurrentImportRegistration(
                            contentHash,
                            ready,
                            start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                            first.get(10, TimeUnit.SECONDS),
                            second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM trade_import_registry
                        WHERE content_hash = ?
                        """,
                Integer.class,
                contentHash)).isEqualTo(1);
    }

    @Test
    @Order(11)
    void concurrentSellAndBuyCancellationNeverCreateNegativePosition()
            throws Exception {
        Instant buyTime = Instant.now().minusSeconds(30);
        MvcResult buy = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "JPM",
                                "BUY",
                                new BigDecimal("10"),
                                BigDecimal.TEN,
                                buyTime)))
                .andExpect(status().isCreated())
                .andReturn();
        String buyId = objectMapper.readTree(
                buy.getResponse().getContentAsString()).path("id").asText();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> sell = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/trades")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(tradeRequest(
                                        PRIMARY_ACCOUNT_ID,
                                        "JPM",
                                        "SELL",
                                        new BigDecimal("6"),
                                        new BigDecimal("12"),
                                        buyTime.plusSeconds(5))))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> cancel = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post(
                                "/api/trades/{id}/cancel",
                                buyId))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                            sell.get(10, TimeUnit.SECONDS),
                            cancel.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }

        List<BigDecimal> quantities = jdbcTemplate.query(
                """
                        SELECT side, quantity
                        FROM trades
                        WHERE account_id = ?
                          AND ticker = 'JPM'
                          AND status = 'BOOKED'
                        ORDER BY executed_at, created_at, id
                        """,
                (resultSet, rowNumber) -> "BUY".equals(
                        resultSet.getString("side"))
                        ? resultSet.getBigDecimal("quantity")
                        : resultSet.getBigDecimal("quantity").negate(),
                PRIMARY_ACCOUNT_ID);
        BigDecimal running = BigDecimal.ZERO;
        for (BigDecimal quantity : quantities) {
            running = running.add(quantity);
            assertThat(running).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    @Test
    @Order(12)
    void concurrentCancellationIsIdempotentAndKeepsFirstTimestamp()
            throws Exception {
        MvcResult buy = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "KO",
                                "BUY",
                                BigDecimal.ONE,
                                BigDecimal.TEN,
                                Instant.now().minusSeconds(10))))
                .andExpect(status().isCreated())
                .andReturn();
        String tradeId = objectMapper.readTree(
                buy.getResponse().getContentAsString()).path("id").asText();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = executor.submit(() ->
                    concurrentCancel(tradeId, ready, start));
            Future<MvcResult> second = executor.submit(() ->
                    concurrentCancel(tradeId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = first.get(10, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(objectMapper.readTree(
                            firstResult.getResponse().getContentAsString())
                    .path("cancelledAt").asText())
                    .isEqualTo(objectMapper.readTree(
                                    secondResult.getResponse()
                                            .getContentAsString())
                            .path("cancelledAt").asText());
        }

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM trades
                        WHERE id = ?
                          AND status = 'CANCELLED'
                          AND cancelled_at IS NOT NULL
                        """,
                Integer.class,
                tradeId)).isEqualTo(1);
    }

    @Test
    @Order(13)
    void concurrentSellsInDifferentAccountsRemainIsolated()
            throws Exception {
        String firstAccount = createAccount(
                "Concurrency One", "Broker", "1001");
        String secondAccount = createAccount(
                "Concurrency Two", "Broker", "1002");
        Instant buyTime = Instant.now().minusSeconds(30);
        for (String accountId : List.of(firstAccount, secondAccount)) {
            mockMvc.perform(post("/api/trades")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tradeRequest(
                                    accountId,
                                    "TSLA",
                                    "BUY",
                                    new BigDecimal("10"),
                                    BigDecimal.TEN,
                                    buyTime)))
                    .andExpect(status().isCreated());
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() ->
                    concurrentSellForAccount(
                            firstAccount, ready, start));
            Future<Integer> second = executor.submit(() ->
                    concurrentSellForAccount(
                            secondAccount, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                            first.get(10, TimeUnit.SECONDS),
                            second.get(10, TimeUnit.SECONDS)))
                    .containsExactly(201, 201);
        }

        for (String accountId : List.of(firstAccount, secondAccount)) {
            mockMvc.perform(get("/api/positions")
                            .param("accountId", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(
                            "$[?(@.ticker == 'TSLA')].quantity")
                            .value(2));
        }
    }

    private int concurrentSell(
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                PRIMARY_ACCOUNT_ID,
                                "CONCUR",
                                "SELL",
                                new BigDecimal("8"),
                                new BigDecimal("12"),
                                Instant.now().minusSeconds(5))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int concurrentSellForAccount(
            String accountId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeRequest(
                                accountId,
                                "TSLA",
                                "SELL",
                                new BigDecimal("8"),
                                new BigDecimal("12"),
                                Instant.now().minusSeconds(5))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private MvcResult concurrentCancel(
            String tradeId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/trades/{id}/cancel", tradeId))
                .andReturn();
    }

    private int concurrentImportRegistration(
            String contentHash,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/trade-imports/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentHash", contentHash,
                                "fileName", "concurrent.csv",
                                "rowCount", 1,
                                "repeatConfirmed", false))))
                .andReturn()
                .getResponse()
                .getStatus();
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
        if (!DATABASE_PREPARED.compareAndSet(false, true)) {
            return;
        }
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
