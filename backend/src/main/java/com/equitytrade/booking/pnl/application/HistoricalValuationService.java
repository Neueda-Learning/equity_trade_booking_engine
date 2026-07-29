package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.marketdata.domain.DailyMarketPrice;
import com.equitytrade.booking.marketdata.domain.HistoricalMarketDataProvider;
import com.equitytrade.booking.marketdata.domain.MarketDataProviderException;
import com.equitytrade.booking.pnl.domain.HistoricalPositionCalculator;
import com.equitytrade.booking.pnl.domain.HistoricalTrade;
import com.equitytrade.booking.pnl.domain.HistoricalTradeSource;
import com.equitytrade.booking.pnl.domain.HistoryRange;
import com.equitytrade.booking.pnl.domain.PnlCalculator;
import com.equitytrade.booking.pnl.domain.PnlPosition;
import com.equitytrade.booking.pnl.domain.PnlQuote;
import com.equitytrade.booking.pnl.domain.PnlResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class HistoricalValuationService {

    private static final int PRIOR_CLOSE_LOOKBACK_DAYS = 14;

    private final HistoricalTradeSource tradeSource;
    private final HistoricalMarketDataProvider priceProvider;
    private final Clock clock;

    public HistoricalValuationService(
            HistoricalTradeSource tradeSource,
            HistoricalMarketDataProvider priceProvider,
            Clock clock) {
        this.tradeSource = tradeSource;
        this.priceProvider = priceProvider;
        this.clock = clock;
    }

    public ValuationHistoryView history(
            UUID accountId,
            HistoryRange range) {
        List<HistoricalTrade> trades = tradeSource.findBooked(accountId);
        if (trades.isEmpty()) {
            return new ValuationHistoryView(
                    range.apiValue(),
                    List.of());
        }

        LocalDate today = LocalDate.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
        LocalDate earliestTradeDate = trades.stream()
                .map(HistoricalTrade::executedAt)
                .map(executedAt -> LocalDate.ofInstant(
                        executedAt,
                        ZoneOffset.UTC))
                .min(Comparator.naturalOrder())
                .orElse(today);
        LocalDate start = range.startDate(
                today,
                earliestTradeDate);
        Map<String, NavigableMap<LocalDate, DailyMarketPrice>> prices =
                prices(
                        trades,
                        start.minusDays(PRIOR_CLOSE_LOOKBACK_DAYS),
                        today);

        List<ValuationHistoryPointView> points =
                start.datesUntil(today.plusDays(1))
                        .map(date -> point(
                                accountId,
                                trades,
                                prices,
                                date))
                        .toList();
        return new ValuationHistoryView(
                range.apiValue(),
                points);
    }

    private ValuationHistoryPointView point(
            UUID accountId,
            List<HistoricalTrade> trades,
            Map<String, NavigableMap<LocalDate, DailyMarketPrice>> prices,
            LocalDate date) {
        Instant nextDay = date.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        List<PnlPosition> positions =
                HistoricalPositionCalculator.calculate(
                        trades,
                        nextDay,
                        accountId == null);
        Map<String, PnlQuote> quotes = new HashMap<>();
        for (PnlPosition position : positions) {
            NavigableMap<LocalDate, DailyMarketPrice> tickerPrices =
                    prices.get(position.ticker());
            Map.Entry<LocalDate, DailyMarketPrice> close =
                    tickerPrices == null
                            ? null
                            : tickerPrices.floorEntry(date);
            if (close == null) {
                continue;
            }
            DailyMarketPrice price = close.getValue();
            quotes.put(
                    position.ticker(),
                    new PnlQuote(
                            position.ticker(),
                            price.close(),
                            price.tradingDate()
                                    .atStartOfDay(ZoneOffset.UTC)
                                    .toInstant(),
                            price.source(),
                            price.mock(),
                            false,
                            false));
        }
        PnlResult result = PnlCalculator.calculate(
                positions,
                quotes);
        return ValuationHistoryPointView.from(
                accountId,
                date,
                result.totals());
    }

    private Map<String, NavigableMap<LocalDate, DailyMarketPrice>> prices(
            List<HistoricalTrade> trades,
            LocalDate from,
            LocalDate to) {
        Map<String, NavigableMap<LocalDate, DailyMarketPrice>> result =
                new LinkedHashMap<>();
        trades.stream()
                .map(HistoricalTrade::ticker)
                .distinct()
                .sorted()
                .forEach(ticker -> {
                    NavigableMap<LocalDate, DailyMarketPrice> byDate =
                            new TreeMap<>();
                    try {
                        priceProvider.fetchDailyCloses(
                                        ticker,
                                        from,
                                        to)
                                .forEach(price -> byDate.put(
                                        price.tradingDate(),
                                        price));
                    } catch (MarketDataProviderException ignored) {
                        // A missing ticker remains unpriced without hiding
                        // the rest of the portfolio history.
                    }
                    result.put(ticker, byDate);
                });
        return result;
    }
}
