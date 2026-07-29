package com.equitytrade.booking.trade.infrastructure.persistence;

import com.equitytrade.booking.trade.domain.TradeImport;
import com.equitytrade.booking.trade.domain.TradeImportRepository;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTradeImportRepositoryAdapter
        implements TradeImportRepository {

    private final SpringDataTradeImportRepository repository;

    public JpaTradeImportRepositoryAdapter(
            SpringDataTradeImportRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TradeImport> findByContentHashForUpdate(
            String contentHash) {
        return repository.findByContentHashForUpdate(contentHash)
                .map(this::toDomain);
    }

    @Override
    public Optional<TradeImport> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id.toString()).map(this::toDomain);
    }

    @Override
    public TradeImport save(TradeImport tradeImport) {
        return toDomain(repository.saveAndFlush(toEntity(tradeImport)));
    }

    private TradeImportJpaEntity toEntity(TradeImport tradeImport) {
        return new TradeImportJpaEntity(
                tradeImport.id().toString(),
                tradeImport.contentHash(),
                tradeImport.firstFileName(),
                tradeImport.rowCount(),
                tradeImport.firstImportedAt()
                        .atOffset(ZoneOffset.UTC)
                        .toLocalDateTime(),
                tradeImport.lastImportedAt()
                        .atOffset(ZoneOffset.UTC)
                        .toLocalDateTime(),
                tradeImport.importCount(),
                tradeImport.status(),
                tradeImport.lastSuccessCount(),
                tradeImport.lastFailureCount());
    }

    private TradeImport toDomain(TradeImportJpaEntity entity) {
        return new TradeImport(
                UUID.fromString(entity.getId()),
                entity.getContentHash(),
                entity.getFirstFileName(),
                entity.getRowCount(),
                entity.getFirstImportedAt().toInstant(ZoneOffset.UTC),
                entity.getLastImportedAt().toInstant(ZoneOffset.UTC),
                entity.getImportCount(),
                entity.getStatus(),
                entity.getLastSuccessCount(),
                entity.getLastFailureCount());
    }
}
