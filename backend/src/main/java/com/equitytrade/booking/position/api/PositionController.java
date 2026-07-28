package com.equitytrade.booking.position.api;

import com.equitytrade.booking.position.application.PositionApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PositionController {

    private final PositionApplicationService positionApplicationService;

    public PositionController(
            PositionApplicationService positionApplicationService) {
        this.positionApplicationService = positionApplicationService;
    }

    @GetMapping("/api/positions")
    public List<PositionResponse> list(
            @RequestParam(required = false) UUID accountId) {
        return responses(positionApplicationService.list(accountId));
    }

    @GetMapping("/api/accounts/{accountId}/positions")
    public List<PositionResponse> listForAccount(
            @PathVariable UUID accountId) {
        return responses(positionApplicationService.list(accountId));
    }

    private List<PositionResponse> responses(
            List<com.equitytrade.booking.position.application.PositionView>
                    positions) {
        return positions.stream().map(PositionResponse::from).toList();
    }
}
