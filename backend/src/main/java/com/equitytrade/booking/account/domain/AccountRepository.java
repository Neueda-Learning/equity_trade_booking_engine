package com.equitytrade.booking.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    List<Account> findAll();

    boolean existsByNameExcludingId(String name, UUID excludedId);
}
