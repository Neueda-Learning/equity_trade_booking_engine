package com.equitytrade.booking.pnl.infrastructure.context;

import com.equitytrade.booking.account.application.AccountApplicationService;
import com.equitytrade.booking.account.application.AccountView;
import com.equitytrade.booking.pnl.domain.DashboardAccount;
import com.equitytrade.booking.pnl.domain.DashboardActivity;
import com.equitytrade.booking.pnl.domain.DashboardContextSource;
import com.equitytrade.booking.trade.application.TradeApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BookingDashboardContextAdapter
        implements DashboardContextSource {

    private final AccountApplicationService accountService;
    private final TradeApplicationService tradeService;

    public BookingDashboardContextAdapter(
            AccountApplicationService accountService,
            TradeApplicationService tradeService) {
        this.accountService = accountService;
        this.tradeService = tradeService;
    }

    @Override
    public List<DashboardAccount> accounts() {
        return accountService.list().stream()
                .map(account -> new DashboardAccount(
                        account.id(),
                        account.name(),
                        "ACTIVE".equals(account.status())))
                .toList();
    }

    @Override
    public void requireAccount(UUID accountId) {
        accountService.get(accountId);
    }

    @Override
    public List<DashboardActivity> recentActivity(
            UUID accountId,
            int limit) {
        Map<UUID, AccountView> accounts = accountService.list().stream()
                .collect(Collectors.toMap(
                        AccountView::id,
                        Function.identity()));
        return tradeService.list(accountId, 0, limit).items().stream()
                .map(trade -> {
                    AccountView account = accounts.get(trade.accountId());
                    return new DashboardActivity(
                            trade.id(),
                            trade.accountId(),
                            account == null
                                    ? "Unknown account"
                                    : account.name(),
                            trade.ticker(),
                            trade.side(),
                            trade.quantity(),
                            trade.status(),
                            trade.executedAt(),
                            trade.cancelledAt());
                })
                .toList();
    }
}
