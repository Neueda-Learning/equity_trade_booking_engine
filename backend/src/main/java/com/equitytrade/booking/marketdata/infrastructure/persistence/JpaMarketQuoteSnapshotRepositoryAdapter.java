package com.equitytrade.booking.marketdata.infrastructure.persistence;

import com.equitytrade.booking.marketdata.domain.MarketQuoteSnapshot;
import com.equitytrade.booking.marketdata.domain.MarketQuoteSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class JpaMarketQuoteSnapshotRepositoryAdapter
        implements MarketQuoteSnapshotRepository {

    private final SpringDataMarketQuoteSnapshotRepository repository;

    public JpaMarketQuoteSnapshotRepositoryAdapter(
            SpringDataMarketQuoteSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public MarketQuoteSnapshot save(MarketQuoteSnapshot snapshot) {
        return toDomain(repository.saveAndFlush(toEntity(snapshot)));
    }

    private MarketQuoteSnapshotJpaEntity toEntity(
            MarketQuoteSnapshot snapshot) {
        return new MarketQuoteSnapshotJpaEntity(
                snapshot.id().toString(),
                snapshot.ticker(),
                snapshot.price(),
                snapshot.previousClose(),
                LocalDateTime.ofInstant(
                        snapshot.marketTimestamp(),
                        ZoneOffset.UTC),
                LocalDateTime.ofInstant(
                        snapshot.fetchedAt(),
                        ZoneOffset.UTC),
                snapshot.source(),
                snapshot.mock(),
                LocalDateTime.ofInstant(
                        snapshot.persistedAt(),
                        ZoneOffset.UTC));
    }

    private MarketQuoteSnapshot toDomain(
            MarketQuoteSnapshotJpaEntity entity) {
        return new MarketQuoteSnapshot(
                UUID.fromString(entity.getId()),
                entity.getTicker(),
                entity.getPrice(),
                entity.getPreviousClose(),
                entity.getMarketTimestamp().toInstant(ZoneOffset.UTC),
                entity.getFetchedAt().toInstant(ZoneOffset.UTC),
                entity.getSource(),
                entity.isMock(),
                entity.getPersistedAt().toInstant(ZoneOffset.UTC));
    }
}
