package com.equitytrade.booking.pnl.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ValuationSnapshotRepository {

    ValuationSnapshot save(ValuationSnapshot snapshot);

    List<ValuationSnapshot> find(
            SnapshotScope scope,
            UUID accountId,
            Instant capturedFrom);
}
