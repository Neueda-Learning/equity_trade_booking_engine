package com.equitytrade.booking.marketdata.infrastructure.provider;

import com.equitytrade.booking.marketdata.domain.InstrumentSearchProvider;
import com.equitytrade.booking.marketdata.domain.InstrumentSearchResult;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DeterministicMockInstrumentSearchProvider
        implements InstrumentSearchProvider {

    private static final List<InstrumentSearchResult> INSTRUMENTS = List.of(
            stock("AAPL", "APPLE INC"),
            stock("AMZN", "AMAZON.COM INC"),
            mock("AUDIT"),
            stock("BRK.B", "BERKSHIRE HATHAWAY INC"),
            mock("CONCUR"),
            mock("FAIL"),
            stock("GOOGL", "ALPHABET INC"),
            stock("JPM", "JPMORGAN CHASE & CO"),
            stock("KO", "COCA-COLA CO"),
            mock("MISS"),
            stock("MSFT", "MICROSOFT CORP"),
            stock("NVDA", "NVIDIA CORP"),
            mock("NOSHRT"),
            mock("PRECISE"),
            mock("RSTRT"),
            new InstrumentSearchResult(
                    "SPY",
                    "SPDR S&P 500 ETF TRUST",
                    "US",
                    "ETF"),
            stock("TSLA", "TESLA INC"));

    @Override
    public List<InstrumentSearchResult> search(
            String query,
            int limit) {
        String normalized = query.strip().toUpperCase(Locale.ROOT);
        return INSTRUMENTS.stream()
                .filter(item -> item.ticker().contains(normalized)
                        || item.name().contains(normalized))
                .sorted(Comparator
                        .comparing((InstrumentSearchResult item) ->
                                !item.ticker().equals(normalized))
                        .thenComparing(InstrumentSearchResult::ticker))
                .limit(limit)
                .toList();
    }

    private static InstrumentSearchResult stock(
            String ticker,
            String name) {
        return new InstrumentSearchResult(
                ticker,
                name,
                "US",
                "Common Stock");
    }

    private static InstrumentSearchResult mock(String ticker) {
        return new InstrumentSearchResult(
                ticker,
                ticker + " MOCK TEST SECURITY",
                "US",
                "Common Stock");
    }
}
