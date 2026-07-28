package com.equitytrade.booking.pnl.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PnlCalculatorTests {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant AS_OF =
            Instant.parse("2026-07-28T09:00:00Z");

    @Test
    void calculatesProfitLossAndZeroPnlUsingDecimal128() {
        PnlResult result = PnlCalculator.calculate(
                List.of(
                        position("GAIN", "3", "10", "30"),
                        position("LOSS", "2", "20", "40"),
                        position("FLAT", "7", "5", "35")),
                Map.of(
                        "GAIN", quote("GAIN", "12.345678"),
                        "LOSS", quote("LOSS", "17.5"),
                        "FLAT", quote("FLAT", "5")));

        assertThat(result.items())
                .extracting(PositionPnl::ticker)
                .containsExactly("FLAT", "GAIN", "LOSS");
        PositionPnl gain = item(result, "GAIN");
        PositionPnl loss = item(result, "LOSS");
        PositionPnl flat = item(result, "FLAT");
        assertThat(gain.marketValue())
                .isEqualByComparingTo("37.037034");
        assertThat(gain.unrealizedPnl())
                .isEqualByComparingTo("7.037034");
        assertThat(loss.unrealizedPnl())
                .isEqualByComparingTo("-5");
        assertThat(flat.unrealizedPnl()).isEqualByComparingTo("0");
        assertThat(result.totals().totalCostBasis())
                .isEqualByComparingTo("105");
        assertThat(result.totals().totalMarketValue())
                .isEqualByComparingTo("107.037034");
        assertThat(result.totals().complete()).isTrue();
    }

    @Test
    void leavesMissingQuoteAmountsNullAndExcludesThemFromTotals() {
        PnlResult result = PnlCalculator.calculate(
                List.of(
                        position("AAPL", "10", "100", "1000"),
                        position("MISSING", "2", "50", "100")),
                Map.of("AAPL", quote("AAPL", "110")));

        PositionPnl missing = item(result, "MISSING");
        assertThat(missing.available()).isFalse();
        assertThat(missing.marketPrice()).isNull();
        assertThat(missing.marketValue()).isNull();
        assertThat(missing.unrealizedPnl()).isNull();
        assertThat(missing.pnlPercent()).isNull();
        assertThat(result.totals().totalCostBasis())
                .isEqualByComparingTo("1000");
        assertThat(result.totals().totalMarketValue())
                .isEqualByComparingTo("1100");
        assertThat(result.totals().pricedPositionCount()).isEqualTo(1);
        assertThat(result.totals().unpricedPositionCount()).isEqualTo(1);
        assertThat(result.totals().complete()).isFalse();
    }

    @Test
    void propagatesQuoteStateAndProtectsPercentageFromZeroDivision() {
        PnlPosition zeroBasis = position(
                "ZERO",
                "1",
                "0",
                "0");
        PnlQuote stale = new PnlQuote(
                "ZERO",
                new BigDecimal("10"),
                AS_OF,
                "MOCK",
                true,
                true,
                true);

        PnlResult result = PnlCalculator.calculate(
                List.of(zeroBasis),
                Map.of("ZERO", stale));

        assertThat(result.items().getFirst().pnlPercent()).isNull();
        assertThat(result.totals().totalPnlPercent()).isNull();
        assertThat(result.totals().mock()).isTrue();
        assertThat(result.totals().stale()).isTrue();
    }

    private PnlPosition position(
            String ticker,
            String quantity,
            String averageCost,
            String costBasis) {
        return new PnlPosition(
                ACCOUNT_ID,
                ticker,
                new BigDecimal(quantity),
                new BigDecimal(averageCost),
                new BigDecimal(costBasis));
    }

    private PnlQuote quote(String ticker, String price) {
        return new PnlQuote(
                ticker,
                new BigDecimal(price),
                AS_OF,
                "MOCK",
                true,
                false,
                false);
    }

    private PositionPnl item(PnlResult result, String ticker) {
        return result.items().stream()
                .filter(item -> ticker.equals(item.ticker()))
                .findFirst()
                .orElseThrow();
    }
}
