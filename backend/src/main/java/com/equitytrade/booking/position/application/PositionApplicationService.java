package com.equitytrade.booking.position.application;

import com.equitytrade.booking.account.application.AccountNotFoundException;
import com.equitytrade.booking.account.domain.AccountRepository;
import com.equitytrade.booking.position.domain.Position;
import com.equitytrade.booking.position.domain.PositionCalculator;
import com.equitytrade.booking.trade.domain.Trade;
import com.equitytrade.booking.trade.domain.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PositionApplicationService {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;

    public PositionApplicationService(
            TradeRepository tradeRepository,
            AccountRepository accountRepository) {
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<PositionView> list(UUID accountId) {
        if (accountId != null && accountRepository.findById(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        List<Position> accountPositions = calculateAccountPositions(
                tradeRepository.findAllBooked());
        if (accountId != null) {
            return accountPositions.stream()
                    .filter(position -> accountId.equals(position.accountId()))
                    .sorted(Comparator.comparing(Position::ticker))
                    .map(PositionView::from)
                    .toList();
        }

        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        accountPositions.stream()
                .sorted(Comparator.comparing(Position::ticker))
                .forEach(position -> aggregates
                        .computeIfAbsent(
                                position.ticker(),
                                ignored -> new Aggregate())
                        .add(position));
        return aggregates.entrySet().stream()
                .map(entry -> PositionView.aggregate(
                        entry.getKey(),
                        entry.getValue().quantity,
                        entry.getValue().costBasis))
                .toList();
    }

    private List<Position> calculateAccountPositions(List<Trade> trades) {
        Map<PositionKey, List<Trade>> grouped = new LinkedHashMap<>();
        for (Trade trade : trades) {
            grouped.computeIfAbsent(
                    new PositionKey(trade.accountId(), trade.ticker()),
                    ignored -> new ArrayList<>()).add(trade);
        }
        return grouped.values().stream()
                .map(PositionCalculator::calculate)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private record PositionKey(UUID accountId, String ticker) {
    }

    private static final class Aggregate {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal costBasis = BigDecimal.ZERO;

        private void add(Position position) {
            quantity = quantity.add(position.quantity(), MATH_CONTEXT);
            costBasis = costBasis.add(position.costBasis(), MATH_CONTEXT);
        }
    }
}
