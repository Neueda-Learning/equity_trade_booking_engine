package com.equitytrade.booking.trade.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTradeRepository extends JpaRepository<TradeJpaEntity, String> {
}
