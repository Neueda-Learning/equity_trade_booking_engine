package com.equitytrade.booking.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

interface SpringDataAccountRepository
        extends JpaRepository<AccountJpaEntity, String> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);

    List<AccountJpaEntity> findAllByOrderByCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AccountJpaEntity account where account.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") String id);
}
