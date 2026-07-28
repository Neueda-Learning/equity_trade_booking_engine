package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.account.application.AccountConflictException;
import com.equitytrade.booking.account.application.AccountNotFoundException;
import com.equitytrade.booking.account.domain.Account;
import com.equitytrade.booking.account.domain.AccountRepository;
import com.equitytrade.booking.account.domain.AccountStatus;
import com.equitytrade.booking.trade.domain.Trade;
import com.equitytrade.booking.trade.domain.TradeRepository;
import com.equitytrade.booking.trade.domain.TradeSide;
import com.equitytrade.booking.trade.domain.TradeValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class TradeApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public TradeApplicationService(
            TradeRepository tradeRepository,
            AccountRepository accountRepository,
            Clock clock) {
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional
    public TradeView book(BookTradeCommand command) {
        if (command.accountId() == null) {
            throw TradeUseCaseValidationException.forField(
                    "accountId", "is required");
        }
        Account account = accountRepository.findById(command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        command.accountId()));
        if (account.status() == AccountStatus.INACTIVE) {
            throw new AccountConflictException(
                    "accountId", "account is inactive");
        }
        try {
            TradeSide side = parseSide(command.side());
            Trade trade = Trade.book(
                    command.accountId(),
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
    public TradePageView list(UUID accountId, int page, int size) {
        if (page < 0) {
            throw TradeUseCaseValidationException.forField(
                    "page", "must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw TradeUseCaseValidationException.forField(
                    "size", "must be between 1 and 100");
        }
        if (accountId != null && accountRepository.findById(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return TradePageView.from(
                tradeRepository.findAll(accountId, page, size));
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
