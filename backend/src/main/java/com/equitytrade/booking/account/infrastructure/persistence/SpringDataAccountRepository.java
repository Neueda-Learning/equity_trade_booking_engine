package com.equitytrade.booking.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataAccountRepository
        extends JpaRepository<AccountJpaEntity, String> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);

    List<AccountJpaEntity> findAllByOrderByCreatedAtAsc();
}
