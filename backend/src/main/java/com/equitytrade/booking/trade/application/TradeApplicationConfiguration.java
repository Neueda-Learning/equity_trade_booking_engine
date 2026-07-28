package com.equitytrade.booking.trade.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TradeApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
