package com.equitytrade.booking.account.application;

import com.equitytrade.booking.account.domain.Account;
import com.equitytrade.booking.account.domain.AccountRepository;
import com.equitytrade.booking.account.domain.AccountValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AccountApplicationService {

    private final AccountRepository accountRepository;
    private final Clock clock;

    public AccountApplicationService(
            AccountRepository accountRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional
    public AccountView create(AccountCommand command) {
        try {
            Account account = Account.create(
                    command.name(),
                    command.broker(),
                    command.accountNumberLast4(),
                    clock.instant());
            ensureUniqueName(account.name(), null);
            return AccountView.from(accountRepository.save(account));
        } catch (AccountValidationException exception) {
            throw AccountUseCaseValidationException.from(exception);
        }
    }

    @Transactional(readOnly = true)
    public List<AccountView> list() {
        return accountRepository.findAll().stream()
                .map(AccountView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID id) {
        return AccountView.from(find(id));
    }

    @Transactional
    public AccountView update(UUID id, AccountCommand command) {
        try {
            Account account = find(id).update(
                    command.name(),
                    command.broker(),
                    command.accountNumberLast4(),
                    clock.instant());
            ensureUniqueName(account.name(), id);
            return AccountView.from(accountRepository.save(account));
        } catch (AccountValidationException exception) {
            throw AccountUseCaseValidationException.from(exception);
        }
    }

    @Transactional
    public AccountView deactivate(UUID id) {
        Account account = find(id);
        Account deactivated = account.deactivate(clock.instant());
        if (deactivated == account) {
            return AccountView.from(account);
        }
        return AccountView.from(accountRepository.save(deactivated));
    }

    private Account find(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    private void ensureUniqueName(String name, UUID excludedId) {
        if (accountRepository.existsByNameExcludingId(name, excludedId)) {
            throw new AccountConflictException("name", "already exists");
        }
    }
}
