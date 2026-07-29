package com.equitytrade.booking.trade.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SpringDataTradeImportRepository
        extends JpaRepository<TradeImportJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select tradeImport
            from TradeImportJpaEntity tradeImport
            where tradeImport.contentHash = :contentHash
            """)
    Optional<TradeImportJpaEntity> findByContentHashForUpdate(
            @Param("contentHash") String contentHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select tradeImport
            from TradeImportJpaEntity tradeImport
            where tradeImport.id = :id
            """)
    Optional<TradeImportJpaEntity> findByIdForUpdate(@Param("id") String id);
}
