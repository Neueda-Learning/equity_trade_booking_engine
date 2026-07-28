package com.equitytrade.booking.pnl.domain;

import java.util.List;
import java.util.UUID;

public interface PnlPositionSource {

    List<PnlPosition> findPositions(UUID accountId);
}
