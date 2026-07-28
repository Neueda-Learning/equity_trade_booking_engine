package com.equitytrade.booking.trade.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Trade(
        UUID id,
        UUID accountId,
        String ticker,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        Instant executedAt,
        TradeStatus status,
        Instant createdAt) {

    private static final Pattern TICKER_PATTERN =
            Pattern.compile("[A-Z][A-Z0-9.-]{0,9}");
    private static final int MAX_SCALE = 6;
    private static final int MAX_INTEGER_DIGITS = 13;

    public Trade {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(ticker);
        Objects.requireNonNull(side);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(tradePrice);
        Objects.requireNonNull(executedAt);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

    public static Trade book(
            UUID accountId,
            String rawTicker,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal tradePrice,
            Instant executedAt,
            Instant now) {
        List<TradeFieldViolation> violations = new ArrayList<>();
        if (accountId == null) {
            violations.add(new TradeFieldViolation("accountId", "is required"));
        }
        String ticker = normalizeTicker(rawTicker, violations);

        if (side == null) {
            violations.add(new TradeFieldViolation("side", "is required"));
        } else if (side != TradeSide.BUY) {
            violations.add(new TradeFieldViolation(
                    "side", "only BUY trades are supported"));
        }

        validateAmount("quantity", quantity, violations);
        validateAmount("tradePrice", tradePrice, violations);

        if (executedAt == null) {
            violations.add(new TradeFieldViolation("executedAt", "is required"));
        } else if (executedAt.isAfter(now.plusSeconds(60))) {
            violations.add(new TradeFieldViolation(
                    "executedAt",
                    "must not be more than 60 seconds in the future"));
        }

        if (!violations.isEmpty()) {
            throw new TradeValidationException(violations);
        }

        return new Trade(
                UUID.randomUUID(),
                accountId,
                ticker,
                side,
                quantity,
                tradePrice,
                executedAt.truncatedTo(ChronoUnit.MICROS),
                TradeStatus.BOOKED,
                now.truncatedTo(ChronoUnit.MICROS));
    }

    private static String normalizeTicker(
            String rawTicker,
            List<TradeFieldViolation> violations) {
        if (rawTicker == null) {
            violations.add(new TradeFieldViolation("ticker", "is required"));
            return "";
        }

        String normalized = rawTicker.strip().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(normalized).matches()) {
            violations.add(new TradeFieldViolation(
                    "ticker",
                    "must match [A-Z][A-Z0-9.-]{0,9}"));
        }
        return normalized;
    }

    private static void validateAmount(
            String field,
            BigDecimal value,
            List<TradeFieldViolation> violations) {
        if (value == null) {
            violations.add(new TradeFieldViolation(field, "is required"));
            return;
        }
        if (value.signum() <= 0) {
            violations.add(new TradeFieldViolation(field, "must be greater than 0"));
        }
        if (value.scale() > MAX_SCALE) {
            violations.add(new TradeFieldViolation(
                    field, "must have at most 6 decimal places"));
        }
        if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            violations.add(new TradeFieldViolation(
                    field, "must have at most 13 integer digits"));
        }
    }
}
