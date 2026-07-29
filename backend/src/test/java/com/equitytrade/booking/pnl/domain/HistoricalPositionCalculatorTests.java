package com.equitytrade.booking.pnl.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalPositionCalculatorTests {

    private static final UUID FIRST_ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void replaysOnlyTradesExecutedBeforeTheValuationDayEnds() {
        List<HistoricalTrade> trades = List.of(
                trade(
                        FIRST_ACCOUNT,
                        HistoricalTradeSide.BUY,
                        "10",
                        "100",
                        "2026-07-15T14:00:00Z"),
                trade(
                        FIRST_ACCOUNT,
                        HistoricalTradeSide.SELL,
                        "4",
                        "110",
                        "2026-07-17T14:00:00Z"));

        List<PnlPosition> beforeSell =
                HistoricalPositionCalculator.calculate(
                        trades,
                        Instant.parse("2026-07-17T00:00:00Z"),
                        false);
        List<PnlPosition> afterSell =
                HistoricalPositionCalculator.calculate(
                        trades,
                        Instant.parse("2026-07-18T00:00:00Z"),
                        false);

        assertThat(beforeSell).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualByComparingTo("10");
            assertThat(position.costBasis()).isEqualByComparingTo("1000");
        });
        assertThat(afterSell).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualByComparingTo("6");
            assertThat(position.costBasis()).isEqualByComparingTo("600");
        });
    }

    @Test
    void aggregatesTheSameTickerAcrossAccounts() {
        List<HistoricalTrade> trades = List.of(
                trade(
                        FIRST_ACCOUNT,
                        HistoricalTradeSide.BUY,
                        "2",
                        "100",
                        "2026-07-15T14:00:00Z"),
                trade(
                        SECOND_ACCOUNT,
                        HistoricalTradeSide.BUY,
                        "3",
                        "120",
                        "2026-07-15T15:00:00Z"));

        List<PnlPosition> positions =
                HistoricalPositionCalculator.calculate(
                        trades,
                        Instant.parse("2026-07-16T00:00:00Z"),
                        true);

        assertThat(positions).singleElement().satisfies(position -> {
            assertThat(position.accountId()).isNull();
            assertThat(position.quantity()).isEqualByComparingTo("5");
            assertThat(position.costBasis()).isEqualByComparingTo("560");
        });
    }

    private HistoricalTrade trade(
            UUID accountId,
            HistoricalTradeSide side,
            String quantity,
            String price,
            String executedAt) {
        Instant executed = Instant.parse(executedAt);
        return new HistoricalTrade(
                UUID.randomUUID(),
                accountId,
                "AAPL",
                side,
                new BigDecimal(quantity),
                new BigDecimal(price),
                executed,
                Instant.parse("2026-07-29T09:00:00Z"));
    }
}
