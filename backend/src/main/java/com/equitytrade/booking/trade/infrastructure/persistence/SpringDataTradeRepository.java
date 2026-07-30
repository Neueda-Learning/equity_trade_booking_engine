package com.equitytrade.booking.trade.infrastructure.persistence;

import com.equitytrade.booking.trade.domain.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataTradeRepository extends JpaRepository<TradeJpaEntity, String> {

    Page<TradeJpaEntity> findByAccountId(String accountId, Pageable pageable);

    @Query("""
            select trade
            from TradeJpaEntity trade
            where trade.id = :id
              and exists (
                  select account.id
                  from AccountJpaEntity account
                  where account.id = trade.accountId
                    and account.deletedAt is null
              )
            """)
    Optional<TradeJpaEntity> findVisibleById(@Param("id") String id);

    @Query("""
            select trade
            from TradeJpaEntity trade
            where exists (
                select account.id
                from AccountJpaEntity account
                where account.id = trade.accountId
                  and account.deletedAt is null
            )
            """)
    Page<TradeJpaEntity> findAllVisible(Pageable pageable);

    List<TradeJpaEntity>
            findByAccountIdAndTickerAndStatusOrderByExecutedAtAscCreatedAtAscIdAsc(
                    String accountId,
                    String ticker,
                    TradeStatus status);

    @Query("""
            select trade
            from TradeJpaEntity trade
            where trade.status = :status
              and exists (
                  select account.id
                  from AccountJpaEntity account
                  where account.id = trade.accountId
                    and account.deletedAt is null
              )
            order by trade.executedAt asc, trade.createdAt asc, trade.id asc
            """)
    List<TradeJpaEntity> findVisibleByStatus(
            @Param("status") TradeStatus status);
}
