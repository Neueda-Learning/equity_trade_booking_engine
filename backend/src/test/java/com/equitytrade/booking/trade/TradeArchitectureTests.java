package com.equitytrade.booking.trade;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.equitytrade.booking",
        importOptions = ImportOption.DoNotIncludeTests.class)
class TradeArchitectureTests {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_trade_layers =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..api..",
                            "..application..",
                            "..infrastructure..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_frameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule api_does_not_depend_on_persistence =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "..infrastructure.persistence..");

    @ArchTest
    static final ArchRule market_data_domain_is_framework_independent =
            noClasses()
                    .that().resideInAPackage("..marketdata.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.fasterxml.jackson..",
                            "io.lettuce..",
                            "redis.clients..",
                            "..marketdata.api..",
                            "..marketdata.application..",
                            "..marketdata.infrastructure..");

    @ArchTest
    static final ArchRule market_data_core_does_not_depend_on_http_or_finnhub =
            noClasses()
                    .that().resideInAnyPackage(
                            "..marketdata.domain..",
                            "..marketdata.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.net.http..",
                            "org.springframework.web.client..",
                            "org.springframework.web.reactive.function.client..",
                            "..marketdata.infrastructure.provider..");

    @ArchTest
    static final ArchRule pnl_domain_is_framework_independent =
            noClasses()
                    .that().resideInAPackage("..pnl.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "com.fasterxml.jackson..",
                            "io.lettuce..",
                            "redis.clients..",
                            "react..",
                            "..pnl.api..",
                            "..pnl.application..",
                            "..pnl.infrastructure..");
}
