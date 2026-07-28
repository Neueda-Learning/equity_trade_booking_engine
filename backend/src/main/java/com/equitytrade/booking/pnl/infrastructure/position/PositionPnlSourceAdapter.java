package com.equitytrade.booking.pnl.infrastructure.position;

import com.equitytrade.booking.pnl.domain.PnlPosition;
import com.equitytrade.booking.pnl.domain.PnlPositionSource;
import com.equitytrade.booking.position.application.PositionApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PositionPnlSourceAdapter implements PnlPositionSource {

    private final PositionApplicationService positionService;

    public PositionPnlSourceAdapter(
            PositionApplicationService positionService) {
        this.positionService = positionService;
    }

    @Override
    public List<PnlPosition> findPositions(UUID accountId) {
        return positionService.list(accountId).stream()
                .map(position -> new PnlPosition(
                        position.accountId(),
                        position.ticker(),
                        position.quantity(),
                        position.averageCost(),
                        position.costBasis()))
                .toList();
    }
}
