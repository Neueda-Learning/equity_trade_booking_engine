package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.DashboardAccount;
import com.equitytrade.booking.pnl.domain.DashboardContextSource;
import com.equitytrade.booking.pnl.domain.HistoryRange;
import com.equitytrade.booking.pnl.domain.PnlResult;
import com.equitytrade.booking.pnl.domain.ValuationSnapshot;
import com.equitytrade.booking.pnl.domain.ValuationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardApplicationService {

    private static final int RECENT_ACTIVITY_LIMIT = 5;

    private final PnlApplicationService pnlService;
    private final DashboardContextSource contextSource;
    private final ValuationSnapshotRepository snapshotRepository;
    private final HistoricalValuationService historicalValuationService;
    private final Clock clock;

    public DashboardApplicationService(
            PnlApplicationService pnlService,
            DashboardContextSource contextSource,
            ValuationSnapshotRepository snapshotRepository,
            HistoricalValuationService historicalValuationService,
            Clock clock) {
        this.pnlService = pnlService;
        this.contextSource = contextSource;
        this.snapshotRepository = snapshotRepository;
        this.historicalValuationService = historicalValuationService;
        this.clock = clock;
    }

    public DashboardView get(UUID accountId) {
        PnlResult result = pnlService.calculate(accountId, false);
        return dashboard(accountId, result, now());
    }

    @Transactional
    public DashboardView refresh(UUID accountId) {
        Instant capturedAt = now();
        PnlResult refreshed = pnlService.calculate(accountId, true);
        snapshotRepository.save(ValuationSnapshot.capture(
                accountId,
                refreshed.totals(),
                capturedAt));

        if (accountId == null) {
            for (DashboardAccount account : contextSource.accounts()) {
                PnlResult accountResult = pnlService.calculate(
                        account.id(),
                        false);
                snapshotRepository.save(ValuationSnapshot.capture(
                        account.id(),
                        accountResult.totals(),
                        capturedAt));
            }
        }
        return dashboard(accountId, refreshed, capturedAt);
    }

    @Transactional
    public void captureScheduled() {
        Instant capturedAt = now();
        PnlResult all = pnlService.calculate(null, false);
        snapshotRepository.save(ValuationSnapshot.capture(
                null,
                all.totals(),
                capturedAt));
        for (DashboardAccount account : contextSource.accounts()) {
            PnlResult accountResult = pnlService.calculate(
                    account.id(),
                    false);
            snapshotRepository.save(ValuationSnapshot.capture(
                    account.id(),
                    accountResult.totals(),
                    capturedAt));
        }
    }

    @Transactional(readOnly = true)
    public ValuationHistoryView history(
            UUID accountId,
            String rawRange) {
        if (accountId != null) {
            contextSource.requireAccount(accountId);
        }
        HistoryRange range;
        try {
            range = HistoryRange.parse(rawRange);
        } catch (IllegalArgumentException exception) {
            throw new PnlValidationException(
                    "range",
                    exception.getMessage());
        }
        return historicalValuationService.history(accountId, range);
    }

    private DashboardView dashboard(
            UUID accountId,
            PnlResult result,
            Instant capturedAt) {
        List<DashboardAccount> accounts = contextSource.accounts();
        List<PositionPnlView> positions = result.items().stream()
                .map(PositionPnlView::from)
                .toList();
        int accountCount = accountId == null
                ? accounts.size()
                : 1;
        int activeAccountCount = accountId == null
                ? Math.toIntExact(accounts.stream()
                        .filter(DashboardAccount::active)
                        .count())
                : Math.toIntExact(accounts.stream()
                        .filter(account -> account.id().equals(accountId))
                        .filter(DashboardAccount::active)
                        .count());
        return new DashboardView(
                PnlTotalsView.from(result.totals()),
                positions,
                accountCount,
                activeAccountCount,
                contextSource.recentActivity(
                                accountId,
                                RECENT_ACTIVITY_LIMIT)
                        .stream()
                        .map(RecentActivityView::from)
                        .toList(),
                DashboardQuoteStatusView.from(positions),
                capturedAt);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
