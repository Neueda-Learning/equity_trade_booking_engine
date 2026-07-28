package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PnlPosition;
import com.equitytrade.booking.pnl.domain.PnlPositionSource;
import com.equitytrade.booking.pnl.domain.PnlQuote;
import com.equitytrade.booking.pnl.domain.PnlQuoteSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PnlApplicationServiceTests {

    private static final UUID ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void resolvesEachTickerIndependentlyAndKeepsPartialResult() {
        PnlPositionSource positions = ignored -> List.of(
                position("AAPL"),
                position("MISSING"));
        PnlQuoteSource quotes = new PnlQuoteSource() {
            @Override
            public Optional<PnlQuote> find(String ticker) {
                return "AAPL".equals(ticker)
                        ? Optional.of(quote(ticker, true, true))
                        : Optional.empty();
            }

            @Override
            public Optional<PnlQuote> refresh(String ticker) {
                return find(ticker);
            }
        };

        PnlView result = new PnlApplicationService(
                positions,
                quotes).get(ACCOUNT_ID);

        assertThat(result.totals().pricedPositionCount()).isEqualTo(1);
        assertThat(result.totals().unpricedPositionCount()).isEqualTo(1);
        assertThat(result.totals().complete()).isFalse();
        assertThat(result.totals().mock()).isTrue();
        assertThat(result.totals().stale()).isTrue();
    }

    @Test
    void refreshForcesEveryDistinctTickerOnce() {
        AtomicInteger refreshes = new AtomicInteger();
        PnlPositionSource positions = ignored -> List.of(
                position("AAPL"),
                position("AAPL"));
        PnlQuoteSource quotes = new PnlQuoteSource() {
            @Override
            public Optional<PnlQuote> find(String ticker) {
                return Optional.empty();
            }

            @Override
            public Optional<PnlQuote> refresh(String ticker) {
                refreshes.incrementAndGet();
                return Optional.of(quote(ticker, false, false));
            }
        };
        PnlApplicationService service = new PnlApplicationService(
                positions,
                quotes);

        service.calculate(ACCOUNT_ID, true);

        assertThat(refreshes).hasValue(1);
    }

    private PnlPosition position(String ticker) {
        return new PnlPosition(
                ACCOUNT_ID,
                ticker,
                BigDecimal.TEN,
                BigDecimal.TEN,
                new BigDecimal("100"));
    }

    private PnlQuote quote(
            String ticker,
            boolean cached,
            boolean stale) {
        return new PnlQuote(
                ticker,
                new BigDecimal("12"),
                Instant.parse("2026-07-28T09:00:00Z"),
                "MOCK",
                true,
                cached,
                stale);
    }
}
