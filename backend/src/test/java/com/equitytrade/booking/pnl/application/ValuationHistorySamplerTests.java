package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PnlTotals;
import com.equitytrade.booking.pnl.domain.ValuationSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ValuationHistorySamplerTests {

    @Test
    void keepsSmallSeriesUnchanged() {
        List<ValuationSnapshot> snapshots = snapshots(3);

        assertThat(ValuationHistorySampler.evenly(snapshots, 5))
                .containsExactlyElementsOf(snapshots);
    }

    @Test
    void limitsLargeSeriesAndKeepsBothEndpoints() {
        List<ValuationSnapshot> snapshots = snapshots(10);

        List<ValuationSnapshot> sampled =
                ValuationHistorySampler.evenly(snapshots, 4);

        assertThat(sampled).hasSize(4);
        assertThat(sampled.getFirst()).isEqualTo(snapshots.getFirst());
        assertThat(sampled.getLast()).isEqualTo(snapshots.getLast());
        assertThat(sampled)
                .extracting(ValuationSnapshot::capturedAt)
                .isSorted();
    }

    private List<ValuationSnapshot> snapshots(int count) {
        PnlTotals totals = new PnlTotals(
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                1,
                1,
                0,
                true,
                false,
                false);
        Instant start = Instant.parse("2026-07-29T00:00:00Z");
        return IntStream.range(0, count)
                .mapToObj(index -> ValuationSnapshot.capture(
                        null,
                        totals,
                        start.plusSeconds(index * 60L)))
                .toList();
    }
}
