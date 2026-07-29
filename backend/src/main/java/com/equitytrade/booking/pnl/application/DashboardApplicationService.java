package com.equitytrade.booking.pnl.application;

import com.equitytrade.booking.pnl.domain.DashboardAccount;
import com.equitytrade.booking.pnl.domain.DashboardContextSource;
import com.equitytrade.booking.pnl.domain.HistoryRange;
import com.equitytrade.booking.pnl.domain.PnlResult;
import com.equitytrade.booking.pnl.domain.SnapshotScope;
import com.equitytrade.booking.pnl.domain.ValuationSnapshot;
import com.equitytrade.booking.pnl.domain.ValuationSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardApplicationService {

    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final int MAX_HISTORY_POINTS = 1_440;

    private final PnlApplicationService pnlService;
    private final DashboardContextSource contextSource;
    private final ValuationSnapshotRepository snapshotRepository;
    private final HistoricalValuationService historicalValuationService;
    private final Clock clock;
    private final DashboardHistorySource historySource;

    public DashboardApplicationService(
            PnlApplicationService pnlService,
            DashboardContextSource contextSource,
            ValuationSnapshotRepository snapshotRepository,
            HistoricalValuationService historicalValuationService,
            Clock clock,
            @Value("${dashboard.history.source:local}")
            String historySource) {
        this.pnlService = pnlService;
        this.contextSource = contextSource;
        this.snapshotRepository = snapshotRepository;
        this.historicalValuationService = historicalValuationService;
        this.clock = clock;
        this.historySource = DashboardHistorySource.parse(historySource);
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
        PnlResult all = pnlService.calculate(null, true);
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

    @Transactional
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
        Instant capturedFrom = capturedFrom(range);
        List<ValuationSnapshot> snapshots = snapshots(
                accountId,
                capturedFrom);
        if (historySource == DashboardHistorySource.LOCAL) {
            return historyView(range, historySource, false, null, snapshots);
        }

        List<ValuationSnapshot> historical =
                historicalSnapshots(snapshots);
        try {
            ValuationHistoryView fetched =
                    historicalValuationService.history(accountId, range);
            fetched.items().stream()
                    .map(ValuationHistoryPointView::toSnapshot)
                    .forEach(snapshotRepository::save);
            List<ValuationSnapshot> persisted = snapshots(
                    accountId,
                    capturedFrom);
            return historyView(
                    range,
                    historySource,
                    false,
                    null,
                    historySource == DashboardHistorySource.PROVIDER
                            ? historicalSnapshots(persisted)
                            : persisted);
        } catch (HistoricalMarketDataUnavailableException exception) {
            List<ValuationSnapshot> fallback =
                    historySource == DashboardHistorySource.PROVIDER
                            ? historical
                            : snapshots;
            if (fallback.isEmpty()) {
                throw exception;
            }
            return historyView(
                    range,
                    historySource,
                    true,
                    exception.failureCategory().name(),
                    fallback);
        }
    }

    private ValuationHistoryView historyView(
            HistoryRange range,
            DashboardHistorySource source,
            boolean fallback,
            String failureCategory,
            List<ValuationSnapshot> snapshots) {
        List<ValuationHistoryPointView> items =
                ValuationHistorySampler.evenly(
                                snapshots,
                                MAX_HISTORY_POINTS)
                .stream()
                .map(ValuationHistoryPointView::from)
                .toList();
        return new ValuationHistoryView(
                range.apiValue(),
                source.name(),
                fallback,
                failureCategory,
                items);
    }

    private List<ValuationSnapshot> snapshots(
            UUID accountId,
            Instant capturedFrom) {
        return snapshotRepository.find(
                accountId == null
                        ? SnapshotScope.ALL
                        : SnapshotScope.ACCOUNT,
                accountId,
                capturedFrom);
    }

    private List<ValuationSnapshot> historicalSnapshots(
            List<ValuationSnapshot> snapshots) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.id().equals(
                        ValuationHistoryPointView.historicalId(
                                snapshot.accountId(),
                                LocalDate.ofInstant(
                                        snapshot.capturedAt(),
                                        ZoneOffset.UTC))))
                .toList();
    }

    private Instant capturedFrom(HistoryRange range) {
        LocalDate today = LocalDate.ofInstant(now(), ZoneOffset.UTC);
        return switch (range) {
            case ONE_DAY -> today.atStartOfDay(ZoneOffset.UTC).toInstant();
            case SEVEN_DAYS -> today.minusDays(6)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
            case THIRTY_DAYS -> today.minusDays(29)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
            case ALL -> null;
        };
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
