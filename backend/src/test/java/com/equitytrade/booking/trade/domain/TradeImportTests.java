package com.equitytrade.booking.trade.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeImportTests {

    private static final String HASH =
            "0123456789abcdef0123456789abcdef"
                    + "0123456789abcdef0123456789abcdef";
    private static final Instant FIRST = Instant.parse(
            "2026-07-29T08:00:00Z");

    @Test
    void derivesAStableVersionEightUuidFromContentHash() {
        TradeImport first = TradeImport.start(HASH, "one.csv", 2, FIRST);
        TradeImport sameContent =
                TradeImport.start(HASH, "renamed.csv", 2, FIRST.plusSeconds(1));

        assertThat(first.id()).isEqualTo(sameContent.id());
        assertThat(first.id().version()).isEqualTo(8);
        assertThat(first.id().variant()).isEqualTo(2);
    }

    @Test
    void tracksRepeatedAndPartialAttemptsWithoutChangingIdentity() {
        TradeImport original = TradeImport.start(HASH, "one.csv", 2, FIRST);
        TradeImport repeated = original.repeat(FIRST.plusSeconds(10));
        TradeImport completed = repeated.complete(
                2,
                1,
                1,
                FIRST.plusSeconds(20));

        assertThat(completed.id()).isEqualTo(original.id());
        assertThat(completed.importCount()).isEqualTo(2);
        assertThat(completed.status()).isEqualTo(TradeImportStatus.PARTIAL);
        assertThat(completed.lastSuccessCount()).isEqualTo(1);
        assertThat(completed.lastFailureCount()).isEqualTo(1);
    }

    @Test
    void rejectsAStaleCompletionAttempt() {
        TradeImport repeated =
                TradeImport.start(HASH, "one.csv", 1, FIRST)
                        .repeat(FIRST.plusSeconds(1));

        assertThatThrownBy(() -> repeated.complete(
                1,
                1,
                0,
                FIRST.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }
}
