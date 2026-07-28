package com.equitytrade.booking.pnl.api;

import com.equitytrade.booking.pnl.application.PnlApplicationService;
import com.equitytrade.booking.pnl.application.PnlView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pnl")
public class PnlController {

    private final PnlApplicationService pnlService;

    public PnlController(PnlApplicationService pnlService) {
        this.pnlService = pnlService;
    }

    @GetMapping
    public PnlView get(
            @RequestParam(required = false) UUID accountId) {
        return pnlService.get(accountId);
    }
}
