package com.equitytrade.booking.trade.api;

import com.equitytrade.booking.trade.application.BookTradeCommand;
import com.equitytrade.booking.trade.application.TradeApplicationService;
import com.equitytrade.booking.trade.application.TradeView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeApplicationService tradeApplicationService;

    public TradeController(TradeApplicationService tradeApplicationService) {
        this.tradeApplicationService = tradeApplicationService;
    }

    @PostMapping
    public ResponseEntity<TradeResponse> create(
            @RequestBody CreateTradeRequest request) {
        TradeView trade = tradeApplicationService.book(new BookTradeCommand(
                request.ticker(),
                request.side(),
                request.quantity(),
                request.tradePrice(),
                request.executedAt()));
        TradeResponse response = TradeResponse.from(trade);
        return ResponseEntity
                .created(URI.create("/api/trades/" + response.id()))
                .body(response);
    }

    @GetMapping
    public TradePageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return TradePageResponse.from(tradeApplicationService.list(page, size));
    }
}
