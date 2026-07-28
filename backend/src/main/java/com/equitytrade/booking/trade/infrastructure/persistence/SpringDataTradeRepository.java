package com.equitytrade.booking.trade.infrastructure.persistence;

import com.equitytrade.booking.trade.domain.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

interface SpringDataTradeRepository extends JpaRepository<TradeJpaEntity, String> {

    Page<TradeJpaEntity> findByAccountId(String accountId, Pageable pageable);

    List<TradeJpaEntity>
            findByAccountIdAndTickerAndStatusOrderByExecutedAtAscCreatedAtAscIdAsc(
                    String accountId,
                    String ticker,
                    TradeStatus status);

    List<TradeJpaEntity>
            findByStatusOrderByExecutedAtAscCreatedAtAscIdAsc(
                    TradeStatus status);
}
