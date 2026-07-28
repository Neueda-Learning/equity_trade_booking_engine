package com.equitytrade.booking.pnl.domain;

import java.util.Optional;

public interface PnlQuoteSource {

    Optional<PnlQuote> find(String ticker);

    Optional<PnlQuote> refresh(String ticker);
}
