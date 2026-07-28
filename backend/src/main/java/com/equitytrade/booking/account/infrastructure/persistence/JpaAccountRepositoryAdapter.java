package com.equitytrade.booking.account.infrastructure.persistence;

import com.equitytrade.booking.account.domain.Account;
import com.equitytrade.booking.account.domain.AccountRepository;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository repository;

    public JpaAccountRepositoryAdapter(SpringDataAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Account save(Account account) {
        return toDomain(repository.saveAndFlush(toEntity(account)));
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return repository.findById(id.toString()).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id.toString()).map(this::toDomain);
    }

    @Override
    public List<Account> findAll() {
        return repository.findAllByOrderByCreatedAtAsc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByNameExcludingId(String name, UUID excludedId) {
        return excludedId == null
                ? repository.existsByName(name)
                : repository.existsByNameAndIdNot(name, excludedId.toString());
    }

    private AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(
                account.id().toString(),
                account.name(),
                account.broker(),
                account.accountNumberLast4(),
                account.baseCurrency(),
                account.status(),
                account.createdAt().atOffset(ZoneOffset.UTC).toLocalDateTime(),
                account.updatedAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    private Account toDomain(AccountJpaEntity entity) {
        return new Account(
                UUID.fromString(entity.getId()),
                entity.getName(),
                entity.getBroker(),
                entity.getAccountNumberLast4(),
                entity.getBaseCurrency(),
                entity.getStatus(),
                entity.getCreatedAt().toInstant(ZoneOffset.UTC),
                entity.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }
}
