package com.equitytrade.booking.pnl.infrastructure.persistence;

import com.equitytrade.booking.pnl.domain.SnapshotScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

interface SpringDataValuationSnapshotRepository
        extends JpaRepository<ValuationSnapshotJpaEntity, String> {

    List<ValuationSnapshotJpaEntity>
            findByScopeTypeAndAccountIdIsNullOrderByCapturedAtAscIdAsc(
                    SnapshotScope scopeType);

    List<ValuationSnapshotJpaEntity>
            findByScopeTypeAndAccountIdIsNullAndCapturedAtGreaterThanEqualOrderByCapturedAtAscIdAsc(
                    SnapshotScope scopeType,
                    LocalDateTime capturedFrom);

    List<ValuationSnapshotJpaEntity>
            findByScopeTypeAndAccountIdOrderByCapturedAtAscIdAsc(
                    SnapshotScope scopeType,
                    String accountId);

    List<ValuationSnapshotJpaEntity>
            findByScopeTypeAndAccountIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAscIdAsc(
                    SnapshotScope scopeType,
                    String accountId,
                    LocalDateTime capturedFrom);
}
