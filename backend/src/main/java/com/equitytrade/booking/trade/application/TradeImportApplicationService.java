package com.equitytrade.booking.trade.application;

import com.equitytrade.booking.trade.domain.TradeImport;
import com.equitytrade.booking.trade.domain.TradeImportRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TradeImportApplicationService {

    private static final Pattern SHA_256 =
            Pattern.compile("^[0-9a-f]{64}$");
    private final TradeImportRepository repository;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public TradeImportApplicationService(
            TradeImportRepository repository,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public TradeImportView register(RegisterTradeImportCommand command) {
        validate(command);
        try {
            return transactions.execute(status -> registerInTransaction(command));
        } catch (DataIntegrityViolationException
                | TransientDataAccessException concurrentInsert) {
            return transactions.execute(status -> repeatOrReject(command));
        }
    }

    public TradeImportView complete(
            UUID id,
            CompleteTradeImportCommand command) {
        validate(command);
        return transactions.execute(status -> {
            TradeImport current = repository.findByIdForUpdate(id)
                    .orElseThrow(() -> new TradeImportNotFoundException(id));
            try {
                return TradeImportView.from(repository.save(current.complete(
                        command.importCount(),
                        command.successCount(),
                        command.failureCount(),
                        clock.instant())));
            } catch (IllegalArgumentException exception) {
                throw new TradeImportValidationException(
                        "result",
                        exception.getMessage());
            } catch (IllegalStateException exception) {
                throw new TradeImportValidationException(
                        "importCount",
                        exception.getMessage());
            }
        });
    }

    private TradeImportView registerInTransaction(
            RegisterTradeImportCommand command) {
        return repository.findByContentHashForUpdate(command.contentHash())
                .map(existing -> repeatOrReject(existing, command))
                .orElseGet(() -> TradeImportView.from(repository.save(
                        TradeImport.start(
                                command.contentHash(),
                                command.fileName().trim(),
                                command.rowCount(),
                                clock.instant()))));
    }

    private TradeImportView repeatOrReject(
            RegisterTradeImportCommand command) {
        TradeImport existing = repository
                .findByContentHashForUpdate(command.contentHash())
                .orElseThrow(() -> new IllegalStateException(
                        "Concurrent import registration was not persisted."));
        return repeatOrReject(existing, command);
    }

    private TradeImportView repeatOrReject(
            TradeImport existing,
            RegisterTradeImportCommand command) {
        if (!command.repeatConfirmed()) {
            throw new TradeImportDuplicateException(
                    TradeImportView.from(existing));
        }
        return TradeImportView.from(repository.save(
                existing.repeat(clock.instant())));
    }

    private void validate(RegisterTradeImportCommand command) {
        String hash = command.contentHash() == null
                ? ""
                : command.contentHash().toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(hash).matches()
                || !hash.equals(command.contentHash())) {
            throw new TradeImportValidationException(
                    "contentHash",
                    "must be a lowercase SHA-256 hash");
        }
        if (command.fileName() == null
                || command.fileName().isBlank()
                || command.fileName().length() > 255) {
            throw new TradeImportValidationException(
                    "fileName",
                    "must contain between 1 and 255 characters");
        }
        if (command.rowCount() < 1 || command.rowCount() > 200) {
            throw new TradeImportValidationException(
                    "rowCount",
                    "must be between 1 and 200");
        }
    }

    private void validate(CompleteTradeImportCommand command) {
        if (command.importCount() < 1) {
            throw new TradeImportValidationException(
                    "importCount",
                    "must be greater than 0");
        }
        if (command.successCount() < 0 || command.failureCount() < 0) {
            throw new TradeImportValidationException(
                    "result",
                    "counts must not be negative");
        }
    }
}
