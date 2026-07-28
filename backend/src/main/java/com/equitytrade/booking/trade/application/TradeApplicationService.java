package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.Trade;
import com.equitytrade.booking.trade.domain.TradeRepository;
import com.equitytrade.booking.trade.domain.TradeSide;
import com.equitytrade.booking.trade.domain.TradeValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class TradeApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TradeRepository tradeRepository;
    private final Clock clock;

    public TradeApplicationService(TradeRepository tradeRepository, Clock clock) {
        this.tradeRepository = tradeRepository;
        this.clock = clock;
    }

    @Transactional
    public TradeView book(BookTradeCommand command) {
        try {
            TradeSide side = parseSide(command.side());
            Trade trade = Trade.book(
                    command.ticker(),
                    side,
                    command.quantity(),
                    command.tradePrice(),
                    command.executedAt(),
                    clock.instant());
            return TradeView.from(tradeRepository.save(trade));
        } catch (TradeValidationException exception) {
            throw TradeUseCaseValidationException.from(exception);
        }
    }

    @Transactional(readOnly = true)
    public TradePageView list(int page, int size) {
        if (page < 0) {
            throw TradeUseCaseValidationException.forField(
                    "page", "must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw TradeUseCaseValidationException.forField(
                    "size", "must be between 1 and 100");
        }
        return TradePageView.from(tradeRepository.findAll(page, size));
    }

    private TradeSide parseSide(String rawSide) {
        if (rawSide == null || rawSide.isBlank()) {
            throw TradeUseCaseValidationException.forField(
                    "side", "is required");
        }
        try {
            return TradeSide.valueOf(rawSide);
        } catch (IllegalArgumentException exception) {
            throw TradeUseCaseValidationException.forField(
                    "side", "must be BUY or SELL");
        }
    }
}
