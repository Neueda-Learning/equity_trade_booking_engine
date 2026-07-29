package com.equitytrade.booking.marketdata.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMarketQuoteSnapshotRepository
        extends JpaRepository<MarketQuoteSnapshotJpaEntity, String> {
}
