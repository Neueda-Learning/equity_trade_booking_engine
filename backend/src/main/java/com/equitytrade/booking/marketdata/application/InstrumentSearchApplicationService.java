package com.equitytrade.booking.marketdata.application;

import com.equitytrade.booking.marketdata.domain.InstrumentSearchProvider;
import com.equitytrade.booking.marketdata.domain.InstrumentSearchResult;
import com.equitytrade.booking.marketdata.domain.MarketDataFailureCategory;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.marketdata.domain.MarketTicker;

import java.util.List;

public class InstrumentSearchApplicationService {

    private static final int MAX_LIMIT = 20;

    private final InstrumentSearchProvider provider;

    public InstrumentSearchApplicationService(
            InstrumentSearchProvider provider) {
        this.provider = provider;
    }

    public List<InstrumentSearchView> search(String rawQuery, int limit) {
        String query = normalizeQuery(rawQuery);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new MarketDataValidationException(
                    "limit",
                    "must be between 1 and " + MAX_LIMIT);
        }
        try {
            return provider.search(query, limit).stream()
                    .map(InstrumentSearchView::from)
                    .toList();
        } catch (MarketDataProviderException exception) {
            if (exception.category() == MarketDataFailureCategory.NOT_FOUND) {
                return List.of();
            }
            throw new InstrumentSearchUnavailableException(
                    exception.category(),
                    exception);
        }
    }

    public String requireExactTicker(String rawTicker) {
        final String ticker;
        try {
            ticker = MarketTicker.normalize(rawTicker);
        } catch (IllegalArgumentException exception) {
            throw new MarketDataValidationException(
                    "ticker",
                    exception.getMessage());
        }
        boolean found = search(ticker, 10).stream()
                .anyMatch(result -> result.ticker().equals(ticker));
        if (!found) {
            throw new MarketDataValidationException(
                    "ticker",
                    "must be selected from a supported US security");
        }
        return ticker;
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new MarketDataValidationException(
                    "q",
                    "is required");
        }
        String query = rawQuery.strip();
        if (query.length() > 64) {
            throw new MarketDataValidationException(
                    "q",
                    "must not exceed 64 characters");
        }
        return query;
    }
}
