package com.equitytrade.booking.pnl.infrastructure.marketdata;

import com.equitytrade.booking.marketdata.application.MarketDataApplicationService;
import com.equitytrade.booking.marketdata.application.MarketDataUnavailableException;
import com.equitytrade.booking.marketdata.application.MarketDataNotFoundException;
import com.equitytrade.booking.marketdata.application.MarketQuoteView;
import com.equitytrade.booking.pnl.domain.PnlQuote;
import com.equitytrade.booking.pnl.domain.PnlQuoteSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MarketDataPnlQuoteSourceAdapter implements PnlQuoteSource {

    private final MarketDataApplicationService marketDataService;

    public MarketDataPnlQuoteSourceAdapter(
            MarketDataApplicationService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Override
    public Optional<PnlQuote> find(String ticker) {
        return resolve(ticker, false);
    }

    @Override
    public Optional<PnlQuote> refresh(String ticker) {
        return resolve(ticker, true);
    }

    private Optional<PnlQuote> resolve(
            String ticker,
            boolean refresh) {
        try {
            MarketQuoteView quote = refresh
                    ? marketDataService.refresh(ticker)
                    : marketDataService.quote(ticker);
            return Optional.of(new PnlQuote(
                    quote.ticker(),
                    quote.price(),
                    quote.marketTimestamp(),
                    quote.source(),
                    quote.mock(),
                    quote.cached(),
                    quote.stale()));
        } catch (MarketDataUnavailableException
                | MarketDataNotFoundException exception) {
            return Optional.empty();
        }
    }
}
