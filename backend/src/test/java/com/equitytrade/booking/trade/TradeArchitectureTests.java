package com.equitytrade.booking.trade;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.equitytrade.booking.trade",
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
}
