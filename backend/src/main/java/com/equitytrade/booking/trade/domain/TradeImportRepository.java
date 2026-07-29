package com.equitytrade.booking.trade.domain;

import java.util.Optional;
import java.util.UUID;

public interface TradeImportRepository {

    Optional<TradeImport> findByContentHashForUpdate(String contentHash);

    Optional<TradeImport> findByIdForUpdate(UUID id);

    TradeImport save(TradeImport tradeImport);
}
