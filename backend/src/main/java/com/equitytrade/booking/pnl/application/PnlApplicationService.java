package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.PnlCalculator;
import com.equitytrade.booking.pnl.domain.PnlPosition;
import com.equitytrade.booking.pnl.domain.PnlPositionSource;
import com.equitytrade.booking.pnl.domain.PnlQuote;
import com.equitytrade.booking.pnl.domain.PnlQuoteSource;
import com.equitytrade.booking.pnl.domain.PnlResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PnlApplicationService {

    private final PnlPositionSource positionSource;
    private final PnlQuoteSource quoteSource;

    public PnlApplicationService(
            PnlPositionSource positionSource,
            PnlQuoteSource quoteSource) {
        this.positionSource = positionSource;
        this.quoteSource = quoteSource;
    }

    public PnlView get(UUID accountId) {
        return PnlView.from(calculate(accountId, false));
    }

    PnlResult calculate(UUID accountId, boolean refresh) {
        List<PnlPosition> positions =
                positionSource.findPositions(accountId);
        Map<String, PnlQuote> quotes = new LinkedHashMap<>();
        positions.stream()
                .map(PnlPosition::ticker)
                .distinct()
                .forEach(ticker -> resolve(ticker, refresh)
                        .ifPresent(quote -> quotes.put(ticker, quote)));
        return PnlCalculator.calculate(positions, quotes);
    }

    private Optional<PnlQuote> resolve(
            String ticker,
            boolean refresh) {
        return refresh
                ? quoteSource.refresh(ticker)
                : quoteSource.find(ticker);
    }
}
