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

    Optional<AccountJpaEntity> findByIdAndDeletedAtIsNull(String id);

    List<AccountJpaEntity> findAllByDeletedAtIsNullOrderByCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from AccountJpaEntity account
            where account.id = :id
              and account.deletedAt is null
            """)
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from AccountJpaEntity account
            where account.name = :name
            """)
    Optional<AccountJpaEntity> findByNameForUpdate(@Param("name") String name);
}
