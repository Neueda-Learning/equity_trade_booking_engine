package com.equitytrade.booking.trade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeTests {

    private static final Instant NOW = Instant.parse("2026-07-28T06:30:00Z");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void normalizesTickerAndBooksBuyTrade() {
        Trade trade = Trade.book(
                ACCOUNT_ID,
                " aapl ",
                TradeSide.BUY,
                new BigDecimal("10.5"),
                new BigDecimal("195.25"),
                NOW,
                NOW);

        assertThat(trade.ticker()).isEqualTo("AAPL");
        assertThat(trade.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(trade.side()).isEqualTo(TradeSide.BUY);
        assertThat(trade.status()).isEqualTo(TradeStatus.BOOKED);
        assertThat(trade.createdAt()).isEqualTo(NOW);
        assertThat(trade.id()).isNotNull();
    }

    @Test
    void rejectsSellTradeWithSideViolation() {
        assertThatThrownBy(() -> Trade.book(
                ACCOUNT_ID,
                "AAPL",
                TradeSide.SELL,
                BigDecimal.ONE,
                BigDecimal.TEN,
                NOW,
                NOW))
                .isInstanceOfSatisfying(
                        TradeValidationException.class,
                        exception -> assertThat(exception.violations())
                                .containsExactly(new TradeFieldViolation(
                                        "side",
                                        "only BUY trades are supported")));
    }

    @Test
    void rejectsInvalidTickerAndAmounts() {
        assertThatThrownBy(() -> Trade.book(
                ACCOUNT_ID,
                "bad ticker!",
                TradeSide.BUY,
                new BigDecimal("0"),
                new BigDecimal("1.0000001"),
                NOW,
                NOW))
                .isInstanceOfSatisfying(
                        TradeValidationException.class,
                        exception -> assertThat(exception.violations())
                                .extracting(TradeFieldViolation::field)
                                .containsExactly(
                                        "ticker",
                                        "quantity",
                                        "tradePrice"));
    }

    @Test
    void allowsSixtySecondClockSkewButRejectsAnythingLater() {
        Trade accepted = Trade.book(
                ACCOUNT_ID,
                "BRK.B",
                TradeSide.BUY,
                BigDecimal.ONE,
                BigDecimal.TEN,
                NOW.plusSeconds(60),
                NOW);

        assertThat(accepted.executedAt()).isEqualTo(NOW.plusSeconds(60));

        assertThatThrownBy(() -> Trade.book(
                ACCOUNT_ID,
                "BRK.B",
                TradeSide.BUY,
                BigDecimal.ONE,
                BigDecimal.TEN,
                NOW.plusSeconds(60).plusNanos(1),
                NOW))
                .isInstanceOfSatisfying(
                        TradeValidationException.class,
                        exception -> assertThat(exception.violations())
                                .extracting(TradeFieldViolation::field)
                                .containsExactly("executedAt"));
    }

    @Test
    void storesTimestampsAtDatabaseMicrosecondPrecision() {
        Instant executedAt = Instant.parse("2026-07-28T06:29:00.123456789Z");
        Instant createdAt = Instant.parse("2026-07-28T06:30:00.987654321Z");

        Trade trade = Trade.book(
                ACCOUNT_ID,
                "AAPL",
                TradeSide.BUY,
                BigDecimal.ONE,
                BigDecimal.TEN,
                executedAt,
                createdAt);

        assertThat(trade.executedAt())
                .isEqualTo(Instant.parse("2026-07-28T06:29:00.123456Z"));
        assertThat(trade.createdAt())
                .isEqualTo(Instant.parse("2026-07-28T06:30:00.987654Z"));
    }
}
