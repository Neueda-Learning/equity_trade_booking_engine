package com.equitytrade.booking.pnl.infrastructure.scheduling;

import com.equitytrade.booking.pnl.application.DashboardApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "dashboard.snapshots.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DashboardSnapshotScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DashboardSnapshotScheduler.class);

    private final DashboardApplicationService dashboardService;

    public DashboardSnapshotScheduler(
            DashboardApplicationService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Scheduled(
            fixedDelayString = "${dashboard.snapshots.interval:1m}",
            initialDelayString = "${dashboard.snapshots.initial-delay:0s}")
    public void capture() {
        try {
            dashboardService.captureScheduled();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Scheduled dashboard snapshot capture failed",
                    exception);
        }
    }
}
