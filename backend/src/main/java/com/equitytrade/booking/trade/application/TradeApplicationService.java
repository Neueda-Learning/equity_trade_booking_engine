package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.account.application.AccountConflictException;
import com.equitytrade.booking.account.application.AccountNotFoundException;
import com.equitytrade.booking.account.domain.Account;
import com.equitytrade.booking.account.domain.AccountRepository;
import com.equitytrade.booking.account.domain.AccountStatus;
import com.equitytrade.booking.position.domain.NegativePositionException;
import com.equitytrade.booking.position.domain.PositionCalculator;
import com.equitytrade.booking.trade.domain.Trade;
import com.equitytrade.booking.trade.domain.TradeCancellationReason;
import com.equitytrade.booking.trade.domain.TradeRepository;
import com.equitytrade.booking.trade.domain.TradeSide;
import com.equitytrade.booking.trade.domain.TradeStatus;
import com.equitytrade.booking.trade.domain.TradeValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TradeApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;
    private final TradeInstrumentValidator instrumentValidator;
    private final TransactionTemplate transactionTemplate;

    public TradeApplicationService(
            TradeRepository tradeRepository,
            AccountRepository accountRepository,
            Clock clock,
            TradeInstrumentValidator instrumentValidator,
            PlatformTransactionManager transactionManager) {
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
        this.instrumentValidator = instrumentValidator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TradeView book(BookTradeCommand command) {
        Trade trade = createTrade(command);
        instrumentValidator.requireSupported(trade.ticker());
        return Objects.requireNonNull(transactionTemplate.execute(
                status -> bookValidated(trade)));
    }

    private TradeView bookValidated(Trade trade) {
        Account account = accountRepository.findByIdForUpdate(trade.accountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        trade.accountId()));
        if (account.status() == AccountStatus.INACTIVE) {
            throw new AccountConflictException(
                    "accountId", "account is inactive");
        }
        if (trade.side() == TradeSide.SELL) {
            ensureValidSequence(
                    withProposedTrade(tradeRepository
                            .findBookedByAccountAndTicker(
                                    trade.accountId(),
                                    trade.ticker()), trade));
        }
        return TradeView.from(tradeRepository.save(trade));
    }

    public TradeView cancel(UUID id) {
        return cancel(id, TradeCancellationReason.CANCELLED, false);
    }

    public TradeView delete(UUID id) {
        return cancel(id, TradeCancellationReason.DELETED, true);
    }

    public AmendTradeResult amend(
            UUID id,
            BookTradeCommand command) {
        Trade replacement = createTrade(command).superseding(id);
        instrumentValidator.requireSupported(replacement.ticker());
        return Objects.requireNonNull(transactionTemplate.execute(
                status -> amendValidated(id, replacement)));
    }

    private AmendTradeResult amendValidated(
            UUID id,
            Trade replacement) {
        Trade initial = tradeRepository.findById(id)
                .orElseThrow(() -> new TradeNotFoundException(id));
        lockAccounts(initial.accountId(), replacement.accountId());
        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new TradeNotFoundException(id));
        if (trade.status() == TradeStatus.CANCELLED) {
            throw new TradeConflictException(
                    "id",
                    "only a booked trade can be amended");
        }
        Account replacementAccount = accountRepository
                .findById(replacement.accountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        replacement.accountId()));
        if (replacementAccount.status() == AccountStatus.INACTIVE) {
            throw new AccountConflictException(
                    "accountId",
                    "account is inactive");
        }
        if (sameBooking(trade, replacement)) {
            throw TradeUseCaseValidationException.forField(
                    "trade",
                    "must contain at least one change");
        }

        validateAmendmentSequences(trade, replacement);
        Trade cancelled = tradeRepository.save(
                trade.cancel(
                        clock.instant(),
                        TradeCancellationReason.AMENDED));
        Trade savedReplacement = tradeRepository.save(replacement);
        return new AmendTradeResult(
                TradeView.from(cancelled),
                TradeView.from(savedReplacement));
    }

    private TradeView cancel(
            UUID id,
            TradeCancellationReason reason,
            boolean strictReason) {
        Trade initial = tradeRepository.findById(id)
                .orElseThrow(() -> new TradeNotFoundException(id));
        return Objects.requireNonNull(transactionTemplate.execute(
                status -> cancelValidated(
                        id,
                        initial.accountId(),
                        reason,
                        strictReason)));
    }

    private TradeView cancelValidated(
            UUID id,
            UUID accountId,
            TradeCancellationReason reason,
            boolean strictReason) {
        accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        accountId));
        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new TradeNotFoundException(id));
        if (trade.status() == TradeStatus.CANCELLED) {
            if (!strictReason || trade.cancellationReason() == reason) {
                return TradeView.from(trade);
            }
            throw new TradeConflictException(
                    "id",
                    "trade was already cancelled for "
                            + trade.cancellationReason().name().toLowerCase());
        }

        if (trade.side() == TradeSide.BUY) {
            List<Trade> remaining = withoutTrade(
                    tradeRepository.findBookedByAccountAndTicker(
                            trade.accountId(),
                            trade.ticker()),
                    trade.id());
            ensureValidSequence(remaining);
        }
        return TradeView.from(
                tradeRepository.save(
                        trade.cancel(clock.instant(), reason)));
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

    private Trade createTrade(BookTradeCommand command) {
        try {
            TradeSide side = parseSide(command.side());
            return Trade.book(
                    command.accountId(),
                    command.ticker(),
                    side,
                    command.quantity(),
                    command.tradePrice(),
                    command.executedAt(),
                    clock.instant());
        } catch (TradeValidationException exception) {
            throw TradeUseCaseValidationException.from(exception);
        }
    }

    private void lockAccounts(UUID first, UUID second) {
        List<UUID> accountIds = List.of(first, second).stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        for (UUID accountId : accountIds) {
            accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
        }
    }

    private void validateAmendmentSequences(
            Trade original,
            Trade replacement) {
        TradeKey originalKey = TradeKey.from(original);
        TradeKey replacementKey = TradeKey.from(replacement);
        List<TradeKey> keys = originalKey.equals(replacementKey)
                ? List.of(originalKey)
                : List.of(originalKey, replacementKey);
        for (TradeKey key : keys) {
            List<Trade> sequence = new ArrayList<>(
                    withoutTrade(
                            tradeRepository.findBookedByAccountAndTicker(
                                    key.accountId(),
                                    key.ticker()),
                            original.id()));
            if (key.equals(replacementKey)) {
                sequence.add(replacement);
            }
            ensureValidSequence(sequence);
        }
    }

    private List<Trade> withoutTrade(
            List<Trade> trades,
            UUID id) {
        return trades.stream()
                .filter(item -> !item.id().equals(id))
                .toList();
    }

    private boolean sameBooking(Trade existing, Trade replacement) {
        return existing.accountId().equals(replacement.accountId())
                && existing.ticker().equals(replacement.ticker())
                && existing.side() == replacement.side()
                && existing.quantity().compareTo(replacement.quantity()) == 0
                && existing.tradePrice().compareTo(replacement.tradePrice()) == 0
                && existing.executedAt().equals(replacement.executedAt());
    }

    private List<Trade> withProposedTrade(
            List<Trade> existing,
            Trade proposed) {
        List<Trade> trades = new ArrayList<>(existing);
        trades.add(proposed);
        return trades;
    }

    private void ensureValidSequence(List<Trade> trades) {
        try {
            PositionCalculator.calculate(trades);
        } catch (NegativePositionException exception) {
            throw new TradeConflictException(
                    "quantity",
                    "insufficient position; available at execution time: "
                            + apiDecimal(exception.available()));
        }
    }

    private BigDecimal apiDecimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return normalized.scale() < 0
                ? normalized.setScale(0)
                : normalized;
    }

    private record TradeKey(UUID accountId, String ticker) {

        static TradeKey from(Trade trade) {
            return new TradeKey(trade.accountId(), trade.ticker());
        }
    }
}
