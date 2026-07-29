package com.equitytrade.booking.pnl.infrastructure.context;

import com.equitytrade.booking.pnl.domain.HistoricalTrade;
import com.equitytrade.booking.pnl.domain.HistoricalTradeSide;
import com.equitytrade.booking.pnl.domain.HistoricalTradeSource;
import com.equitytrade.booking.trade.domain.TradeRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class BookingHistoricalTradeSourceAdapter
        implements HistoricalTradeSource {

    private final TradeRepository tradeRepository;

    public BookingHistoricalTradeSourceAdapter(
            TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public List<HistoricalTrade> findBooked(UUID accountId) {
        return tradeRepository.findAllBooked().stream()
                .filter(trade -> accountId == null
                        || accountId.equals(trade.accountId()))
                .map(trade -> new HistoricalTrade(
                        trade.id(),
                        trade.accountId(),
                        trade.ticker(),
                        HistoricalTradeSide.valueOf(
                                trade.side().name()),
                        trade.quantity(),
                        trade.tradePrice(),
                        trade.executedAt(),
                        trade.createdAt()))
                .toList();
    }
}
