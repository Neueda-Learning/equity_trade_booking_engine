package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.ValuationSnapshot;

import java.util.ArrayList;
import java.util.List;

final class ValuationHistorySampler {

    private ValuationHistorySampler() {
    }

    static List<ValuationSnapshot> evenly(
            List<ValuationSnapshot> snapshots,
            int maximumPoints) {
        if (maximumPoints < 2) {
            throw new IllegalArgumentException(
                    "History point limit must be at least two");
        }
        if (snapshots.size() <= maximumPoints) {
            return List.copyOf(snapshots);
        }

        List<ValuationSnapshot> sampled =
                new ArrayList<>(maximumPoints);
        int lastIndex = snapshots.size() - 1;
        int lastSampleIndex = maximumPoints - 1;
        for (int index = 0; index < maximumPoints; index++) {
            int sourceIndex = Math.toIntExact(Math.round(
                    (double) index * lastIndex / lastSampleIndex));
            sampled.add(snapshots.get(sourceIndex));
        }
        return List.copyOf(sampled);
    }
}
