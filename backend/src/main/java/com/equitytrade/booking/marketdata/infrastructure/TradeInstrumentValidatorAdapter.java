package com.equitytrade.booking.marketdata.infrastructure;

import com.equitytrade.booking.marketdata.application.InstrumentSearchApplicationService;
import com.equitytrade.booking.trade.application.TradeInstrumentValidator;
import org.springframework.stereotype.Component;

@Component
public class TradeInstrumentValidatorAdapter
        implements TradeInstrumentValidator {

    private final InstrumentSearchApplicationService service;

    public TradeInstrumentValidatorAdapter(
            InstrumentSearchApplicationService service) {
        this.service = service;
    }

    @Override
    public String requireSupported(String ticker) {
        return service.requireExactTicker(ticker);
    }
}
