package com.equitytrade.booking.pnl.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryRangeTests {

    private static final Instant NOW =
            Instant.parse("2026-07-28T09:00:00Z");

    @Test
    void calculatesUtcRangeBoundariesExactly() {
        assertThat(HistoryRange.ONE_DAY.capturedFrom(NOW))
                .contains(Instant.parse("2026-07-27T09:00:00Z"));
        assertThat(HistoryRange.SEVEN_DAYS.capturedFrom(NOW))
                .contains(Instant.parse("2026-07-21T09:00:00Z"));
        assertThat(HistoryRange.THIRTY_DAYS.capturedFrom(NOW))
                .contains(Instant.parse("2026-06-28T09:00:00Z"));
        assertThat(HistoryRange.ALL.capturedFrom(NOW)).isEmpty();
    }

    @Test
    void parsesOnlyDocumentedValues() {
        assertThat(HistoryRange.parse("1D"))
                .isEqualTo(HistoryRange.ONE_DAY);
        assertThat(HistoryRange.parse("ALL"))
                .isEqualTo(HistoryRange.ALL);
        assertThatThrownBy(() -> HistoryRange.parse("1Y"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must be one of 1D, 7D, 30D or ALL");
    }
}
