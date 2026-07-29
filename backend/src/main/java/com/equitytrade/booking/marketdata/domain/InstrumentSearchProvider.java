package com.equitytrade.booking.marketdata.domain;

import java.util.List;

public interface InstrumentSearchProvider {

    List<InstrumentSearchResult> search(String query, int limit);
}
