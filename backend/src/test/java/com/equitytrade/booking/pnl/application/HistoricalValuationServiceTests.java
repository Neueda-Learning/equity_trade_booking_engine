package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.marketdata.domain.DailyMarketPrice;
import com.equitytrade.booking.marketdata.domain.HistoricalMarketDataProvider;
import com.equitytrade.booking.pnl.domain.HistoricalTrade;
import com.equitytrade.booking.pnl.domain.HistoricalTradeSide;
import com.equitytrade.booking.pnl.domain.HistoricalTradeSource;
import com.equitytrade.booking.pnl.domain.HistoryRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalValuationServiceTests {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-07-29T09:00:00Z");

    @Test
    void backfillsByExecutionDateNotOperationDate() {
        HistoricalTrade trade = new HistoricalTrade(
                UUID.randomUUID(),
                ACCOUNT_ID,
                "AAPL",
                HistoricalTradeSide.BUY,
                BigDecimal.ONE,
                new BigDecimal("100"),
                Instant.parse("2026-07-15T06:37:00Z"),
                Instant.parse("2026-07-29T06:37:00Z"));
        HistoricalValuationService service = service(List.of(trade));

        ValuationHistoryView history = service.history(
                ACCOUNT_ID,
                HistoryRange.THIRTY_DAYS);

        assertThat(history.items()).hasSize(30);
        assertThat(history.items())
                .filteredOn(point -> point.valuationDate()
                        .isBefore(LocalDate.parse("2026-07-15")))
                .allSatisfy(point -> {
                    assertThat(point.positionCount()).isZero();
                    assertThat(point.totalMarketValue())
                            .isEqualByComparingTo("0");
                });
        ValuationHistoryPointView executionDay = history.items().stream()
                .filter(point -> point.valuationDate()
                        .equals(LocalDate.parse("2026-07-15")))
                .findFirst()
                .orElseThrow();
        assertThat(executionDay.totalCostBasis())
                .isEqualByComparingTo("100");
        assertThat(executionDay.totalMarketValue())
                .isEqualByComparingTo("120");
        assertThat(executionDay.unrealizedPnl())
                .isEqualByComparingTo("20");
    }

    @Test
    void carriesTheLastCloseAcrossWeekendAndOneDayReturnsTodayOnly() {
        HistoricalTrade trade = new HistoricalTrade(
                UUID.randomUUID(),
                ACCOUNT_ID,
                "AAPL",
                HistoricalTradeSide.BUY,
                BigDecimal.ONE,
                new BigDecimal("100"),
                Instant.parse("2026-07-24T06:00:00Z"),
                Instant.parse("2026-07-29T06:00:00Z"));
        HistoricalMarketDataProvider provider = (ticker, from, to) -> List.of(
                new DailyMarketPrice(
                        ticker,
                        LocalDate.parse("2026-07-24"),
                        new BigDecimal("110"),
                        "TEST",
                        false),
                new DailyMarketPrice(
                        ticker,
                        LocalDate.parse("2026-07-27"),
                        new BigDecimal("120"),
                        "TEST",
                        false),
                new DailyMarketPrice(
                        ticker,
                        LocalDate.parse("2026-07-29"),
                        new BigDecimal("125"),
                        "TEST",
                        false));
        HistoricalValuationService service = service(
                List.of(trade),
                provider);

        ValuationHistoryView week = service.history(
                ACCOUNT_ID,
                HistoryRange.SEVEN_DAYS);
        ValuationHistoryView oneDay = service.history(
                ACCOUNT_ID,
                HistoryRange.ONE_DAY);

        assertThat(week.items()).hasSize(7);
        assertThat(valueOn(week, "2026-07-25"))
                .isEqualByComparingTo("110");
        assertThat(valueOn(week, "2026-07-26"))
                .isEqualByComparingTo("110");
        assertThat(valueOn(week, "2026-07-27"))
                .isEqualByComparingTo("120");
        assertThat(oneDay.items()).singleElement()
                .extracting(ValuationHistoryPointView::valuationDate)
                .isEqualTo(LocalDate.parse("2026-07-29"));
    }

    private BigDecimal valueOn(
            ValuationHistoryView history,
            String date) {
        return history.items().stream()
                .filter(point -> point.valuationDate()
                        .equals(LocalDate.parse(date)))
                .findFirst()
                .orElseThrow()
                .totalMarketValue();
    }

    private HistoricalValuationService service(
            List<HistoricalTrade> trades) {
        HistoricalMarketDataProvider provider = (ticker, from, to) ->
                from.datesUntil(to.plusDays(1))
                        .map(date -> new DailyMarketPrice(
                                ticker,
                                date,
                                new BigDecimal("120"),
                                "TEST",
                                false))
                        .toList();
        return service(trades, provider);
    }

    private HistoricalValuationService service(
            List<HistoricalTrade> trades,
            HistoricalMarketDataProvider provider) {
        HistoricalTradeSource source = ignored -> trades;
        return new HistoricalValuationService(
                source,
                provider,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
