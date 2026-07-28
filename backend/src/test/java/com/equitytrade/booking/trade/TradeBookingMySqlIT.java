package com.equitytrade.booking.trade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("equity_booking")
                    .withUsername("equity_app")
                    .withPassword("integration_password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
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
        jdbcTemplate.update("DELETE FROM trades");
    }

    @Test
    void flywayCreatesExpectedMySqlSchema() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('1', '2') AND success = 1
                        """,
                Integer.class);
        Integer tradeTableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'trades'
                        """,
                Integer.class);

        assertThat(successfulMigrations).isEqualTo(2);
        assertThat(tradeTableCount).isEqualTo(1);
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
                                " audit ",
                                "BUY",
                                quantity,
                                tradePrice,
                                executedAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("AUDIT"))
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

        MvcResult listResult = mockMvc.perform(get("/api/trades?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id")
                        .value(tradeId.toString()))
                .andExpect(jsonPath("$.items[0].ticker").value("AUDIT"))
                .andReturn();
        JsonNode listed = readExactJson(
                listResult.getResponse().getContentAsString()).path("items").path(0);
        assertThat(listed.path("quantity").decimalValue())
                .isEqualByComparingTo(quantity);
        assertThat(listed.path("tradePrice").decimalValue())
                .isEqualByComparingTo(tradePrice);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                        SELECT ticker, quantity, trade_price
                        FROM trades
                        WHERE id = ?
                        """,
                tradeId.toString());
        assertThat(stored.get("ticker")).isEqualTo("AUDIT");
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

    private String tradeRequest(
            String ticker,
            String side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            Instant executedAt) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
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

    private JsonNode readExactJson(String json) throws Exception {
        return objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(json);
    }
}
