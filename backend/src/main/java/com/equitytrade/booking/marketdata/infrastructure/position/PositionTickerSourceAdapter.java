package com.equitytrade.booking.marketdata.infrastructure.position;

import com.equitytrade.booking.marketdata.domain.PositionTickerSource;
import com.equitytrade.booking.position.application.PositionApplicationService;
import com.equitytrade.booking.position.application.PositionView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PositionTickerSourceAdapter implements PositionTickerSource {

    private final PositionApplicationService positionApplicationService;

    public PositionTickerSourceAdapter(
            PositionApplicationService positionApplicationService) {
        this.positionApplicationService = positionApplicationService;
    }

    @Override
    public List<String> findTickers(UUID accountId) {
        return positionApplicationService.list(accountId).stream()
                .map(PositionView::ticker)
                .distinct()
                .sorted()
                .toList();
    }
}
