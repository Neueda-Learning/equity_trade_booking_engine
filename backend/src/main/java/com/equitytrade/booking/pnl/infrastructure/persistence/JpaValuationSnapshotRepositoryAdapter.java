package com.equitytrade.booking.pnl.infrastructure.persistence;

import com.equitytrade.booking.pnl.domain.SnapshotScope;
import com.equitytrade.booking.pnl.domain.ValuationSnapshot;
import com.equitytrade.booking.pnl.domain.ValuationSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaValuationSnapshotRepositoryAdapter
        implements ValuationSnapshotRepository {

    private final SpringDataValuationSnapshotRepository repository;

    public JpaValuationSnapshotRepositoryAdapter(
            SpringDataValuationSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public ValuationSnapshot save(ValuationSnapshot snapshot) {
        return toDomain(repository.saveAndFlush(toEntity(snapshot)));
    }

    @Override
    public List<ValuationSnapshot> find(
            SnapshotScope scope,
            UUID accountId,
            Instant capturedFrom) {
        LocalDateTime from = capturedFrom == null
                ? null
                : LocalDateTime.ofInstant(capturedFrom, ZoneOffset.UTC);
        List<ValuationSnapshotJpaEntity> entities;
        if (accountId == null) {
            entities = from == null
                    ? repository
                            .findByScopeTypeAndAccountIdIsNullOrderByCapturedAtAscIdAsc(
                                    scope)
                    : repository
                            .findByScopeTypeAndAccountIdIsNullAndCapturedAtGreaterThanEqualOrderByCapturedAtAscIdAsc(
                                    scope,
                                    from);
        } else {
            entities = from == null
                    ? repository
                            .findByScopeTypeAndAccountIdOrderByCapturedAtAscIdAsc(
                                    scope,
                                    accountId.toString())
                    : repository
                            .findByScopeTypeAndAccountIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAscIdAsc(
                                    scope,
                                    accountId.toString(),
                                    from);
        }
        return entities.stream().map(this::toDomain).toList();
    }

    private ValuationSnapshotJpaEntity toEntity(
            ValuationSnapshot snapshot) {
        return new ValuationSnapshotJpaEntity(
                snapshot.id().toString(),
                snapshot.scopeType(),
                snapshot.accountId() == null
                        ? null
                        : snapshot.accountId().toString(),
                snapshot.totalCostBasis(),
                snapshot.totalMarketValue(),
                snapshot.unrealizedPnl(),
                snapshot.positionCount(),
                snapshot.pricedPositionCount(),
                snapshot.complete(),
                snapshot.mock(),
                snapshot.stale(),
                LocalDateTime.ofInstant(
                        snapshot.capturedAt(),
                        ZoneOffset.UTC));
    }

    private ValuationSnapshot toDomain(
            ValuationSnapshotJpaEntity entity) {
        return new ValuationSnapshot(
                UUID.fromString(entity.getId()),
                entity.getScopeType(),
                entity.getAccountId() == null
                        ? null
                        : UUID.fromString(entity.getAccountId()),
                entity.getTotalCostBasis(),
                entity.getTotalMarketValue(),
                entity.getUnrealizedPnl(),
                entity.getPositionCount(),
                entity.getPricedPositionCount(),
                entity.isComplete(),
                entity.isMock(),
                entity.isStale(),
                entity.getCapturedAt().toInstant(ZoneOffset.UTC));
    }
}
