package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.DashboardAccount;
import com.equitytrade.booking.pnl.domain.DashboardContextSource;
import com.equitytrade.booking.pnl.domain.PnlResult;
import com.equitytrade.booking.pnl.domain.PnlTotals;
import com.equitytrade.booking.pnl.domain.ValuationSnapshot;
import com.equitytrade.booking.pnl.domain.ValuationSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardApplicationServiceTests {

    @Test
    void scheduledCaptureForceRefreshesTickersOnceThenReusesQuotesPerAccount() {
        PnlApplicationService pnlService =
                mock(PnlApplicationService.class);
        DashboardContextSource contextSource =
                mock(DashboardContextSource.class);
        ValuationSnapshotRepository snapshotRepository =
                mock(ValuationSnapshotRepository.class);
        HistoricalValuationService historicalValuationService =
                mock(HistoricalValuationService.class);
        UUID accountId = UUID.randomUUID();
        PnlResult result = emptyResult();

        when(contextSource.accounts()).thenReturn(List.of(
                new DashboardAccount(accountId, "Primary", true)));
        when(pnlService.calculate(null, true)).thenReturn(result);
        when(pnlService.calculate(accountId, false)).thenReturn(result);

        DashboardApplicationService service =
                new DashboardApplicationService(
                        pnlService,
                        contextSource,
                        snapshotRepository,
                        historicalValuationService,
                        Clock.fixed(
                                Instant.parse("2026-07-29T12:00:00Z"),
                                ZoneOffset.UTC),
                        "local");

        service.captureScheduled();

        verify(pnlService).calculate(null, true);
        verify(pnlService).calculate(accountId, false);
        verify(snapshotRepository, times(2))
                .save(any(ValuationSnapshot.class));
    }

    private PnlResult emptyResult() {
        return new PnlResult(
                List.of(),
                new PnlTotals(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        0,
                        0,
                        0,
                        true,
                        false,
                        false));
    }
}
